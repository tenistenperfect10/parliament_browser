package org.texttechnology.parliament_browser_6_4.data.Impl;

import com.mongodb.BasicDBObject;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.texttechnology.parliament_browser_6_4.data.*;
import org.texttechnology.parliament_browser_6_4.data.Impl.file.Fraction_File_Impl;
import org.texttechnology.parliament_browser_6_4.data.Impl.file.Party_File_Impl;
import org.texttechnology.parliament_browser_6_4.data.Impl.file.Speaker_File_Impl;
import org.texttechnology.parliament_browser_6_4.data.Impl.file.Speaker_Plain_File_Impl;
import org.texttechnology.parliament_browser_6_4.data.Impl.mongodb.*;
import org.texttechnology.parliament_browser_6_4.helper.MongoDBConfig;
import org.texttechnology.parliament_browser_6_4.helper.MongoDBConnectionHandler;
import org.texttechnology.parliament_browser_6_4.helper.NLPHelper;
import org.w3c.dom.Node;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Sorts.descending;

/**
 * Implementation of the InsightFactory interface for managing user data within a MongoDB database.
 * Provides methods to query, save, update, and delete user information.
 * @author Giuseppe Abrami
 */
public class InsightFactory_Impl implements InsightFactory {

    private MongoDBConnectionHandler dbConnectionHandler = null;

    private MongoCollection<Document> userCollection = null ;

    private MongoCollection<Document> speakerCollection = null;

    private MongoCollection<Document> commentCollection = null;

    private MongoCollection<Document> speechCollection = null;

    private MongoCollection<Document> abgeordnterCollection = null;

    private MongoCollection<Document> meetingCollection = null;

    private NLPHelper nlpHelper = null;

    // Variables for storing the protocol informations
    private Set<Speaker> pSpeaker = new HashSet<>();
    private Set<PlenaryProtocol> pProtocols = new HashSet<>();
    private Set<Fraction> pFractions = new HashSet<>();
    private Set<Party> pParties = new HashSet<>();

    private Set<Speech> pSpeeches = new HashSet<>();

    /**
     * Default constructor
     */
    @Override
    public Set<Speaker> getSpeakers(){
        return this.pSpeaker;
    }

    @Override
    public Set<Speech> getpSpeeches() {
        return this.pSpeeches;
    }

    @Override
    public void addSpeeches(Speech speech) {
        pSpeeches.add(speech);

    }

    @Override
    public Set<Speaker> getSpeakers(Fraction pFraction) {
        return this.getSpeakers().stream().filter(s->{
            if(s.getFraction()!=null) {
                return s.getFraction().equals(pFraction);
            }
            return false;
        }).collect(Collectors.toSet());
    }

    @Override
    public Set<PlenaryProtocol> getProtocols(){
        return this.pProtocols;
    }

    @Override
    public Set<PlenaryProtocol> getProtocols(int iWP) {
        return getProtocols().stream().filter(p->p.getWahlperiode()==iWP).collect(Collectors.toSet());
    }

    @Override
    public void addProtocol(PlenaryProtocol pProtocol) {
        this.pProtocols.add(pProtocol);
    }

    @Override
    public Set<Fraction> getFractions(){
        return pFractions;
    }

    @Override
    public Set<Party> getParties(){
        return this.pParties;
    }

    @Override
    public Party getParty(String sName) {

        List<Party> sList = this.getParties().stream().filter(s->s.getName().equalsIgnoreCase(sName)).collect(Collectors.toList());

        if(sList.size()==1){
            return sList.get(0);
        }
        else{
            Party pParty = new Party_File_Impl(sName);
            this.pParties.add(pParty);
            return pParty;
        }

    }

    @Override
    public Speaker getSpeaker(String sId) {

        List<Speaker> sList = this.getSpeakers().stream().filter(s->s.getID().equals(sId)).collect(Collectors.toList());

        if(sList.size()==1){
            return sList.get(0);
        }

        return null;

    }

    @Override
    public void addspeaker(Speaker pSpeaker){
        this.pSpeaker.add(pSpeaker);
    }

    public Speaker getSpeakerByName(String sValue){

        List<Speaker> sList = this.getSpeakers().stream().filter(s->{
            return s.getName().equalsIgnoreCase(Speaker_Plain_File_Impl.transform(sValue));
        }).collect(Collectors.toList());

        if(sList.size()==1){
            return sList.get(0);
        }
        return null;

    }

    @Override
    public Speaker getSpeaker(Node pNode) {

        Speaker pSpeaker = null;

        // if speaker is a complex node
        if(!pNode.getNodeName().equalsIgnoreCase("name")){
            String sID = pNode.getAttributes().getNamedItem("id").getTextContent();

            pSpeaker= getSpeaker(sID);

            if(pSpeaker==null){
                Speaker_File_Impl nSpeaker = new Speaker_File_Impl(this, pNode);
                this.pSpeaker.add(nSpeaker);
                pSpeaker = nSpeaker;
            }
        }
        // if not...
        else{
            pSpeaker = getSpeakerByName(pNode.getTextContent());

            if(pSpeaker==null){
                Speaker_Plain_File_Impl plainSpeaker = new Speaker_Plain_File_Impl(this);
                plainSpeaker.setName(pNode.getTextContent());

                this.pSpeaker.add(plainSpeaker);
                pSpeaker = plainSpeaker;
            }

        }

        return pSpeaker;
    }

    @Override
    public Fraction getFraction(String sName) {
        /*
         * search in fractions if there is a fraction with this name?
         * Attention: Since in Bündnis 90/Die Grünen partly other characters are used, here a small trick is used and
         * not checked for the simultaneity of the name of the faction but only for their same beginning.
         */
        List<Fraction> sList = this.getFractions().stream().filter(s->{
            if(s.getName().startsWith(sName.substring(0, 3))){
                return true;
            }
            return s.getName().equalsIgnoreCase(sName.trim());
        }).collect(Collectors.toList());

        if(sList.size()==1){
            return sList.get(0);
        }

        return null;
    }

    @Override
    public Fraction getFraction(Node pNode) {
        String sName = pNode.getTextContent();

        Fraction pFraction = getFraction(sName);

        if(pFraction==null){
            // if fraction not exist, create
            pFraction = new Fraction_File_Impl(pNode);

            this.pFractions.add(pFraction);
        }

        return pFraction;
    }

    public InsightFactory_Impl(MongoDBConnectionHandler dbConnectionHandler){
        this.dbConnectionHandler = dbConnectionHandler;
    }


    public InsightFactory_Impl() throws Exception {

        this.nlpHelper = new NLPHelper();
    }

    /**
     * Retrieves the MongoDBConnectionHandler for database interactions.
     *
     * @return The current instance of MongoDBConnectionHandler.
     */
    public MongoDBConnectionHandler getMongoConnection() {
        return this.dbConnectionHandler;
    }


    /**
     * Creates a database connection using the provided MongoDB configuration.
     *
     * @param pConfig The MongoDB configuration to use for the connection.
     */
    public void createDatabaseConnection(MongoDBConfig pConfig) {
        this.dbConnectionHandler = new MongoDBConnectionHandler(pConfig);

    }


    /**
     * Adding Speech
     *
     * @param pSpeech
     */
    @Override
    public void addSpeech(Speech pSpeech) {
        this.dbConnectionHandler.insertSpeech(pSpeech);
        addSpeaker(pSpeech.getSpeaker());


    }

    /**
     * Adding Comment
     *
     * @param pComment
     */
    @Override
    public void addComment(Comment pComment) {

        this.dbConnectionHandler.insertComment(pComment);

    }

    /**
     * Add Speaker
     *
     * @param pSpeaker
     */
    @Override
    public void addSpeaker(Speaker pSpeaker) {

        this.dbConnectionHandler.insertSpeaker(pSpeaker);

    }

    /**
     * get speech from mongoDB
     * @return
     */
    @Override
    public List<Speech> getSpeeches() {
        List<Speech> sList = new ArrayList<>(0);
        MongoCursor<Document> cursor = this.dbConnectionHandler.queryDocuments(BasicDBObject.parse("{}"),"speech");
        cursor.forEachRemaining(document -> {
            sList.add(new Speech_MongoDB_Impl(this,document));
        });
        return sList;
    }

    /**
     * get comments from mongoDB
     * @return
     */
    @Override
    public List<Comment> getComments() {
        List<Comment> cList = new ArrayList<>(0);
        MongoCursor<Document> cursor = this.dbConnectionHandler.queryDocuments(BasicDBObject.parse("{}"),"comment");
        cursor.forEachRemaining(document -> {
            cList.add(new Comment_MongoDB_Impl(this,document));
        });
        return cList;
    }

    /**
     * find comment by given ID
     * @param commentIdList
     * @return
     */

    @Override
    public List<Document> findByIds(List<String> commentIdList) {
        this.commentCollection = dbConnectionHandler.getCollection("comment");
        return commentCollection.find(in("_id", commentIdList)).into(new ArrayList<>());
    }

    /**
     * find speaker by given id
     * @param commentIdList
     * @return
     */

    @Override
    public AggregateIterable<Document> findByIdsWithSpeaker(List<String> commentIdList) {
        this.commentCollection = dbConnectionHandler.getCollection("comment");
        return commentCollection.aggregate(Arrays.asList(
                // First match the eligible speech records
                new Document("$match", in("_id", commentIdList)),

                // Associate the speaker field in speech to the speaker collection
                new Document("$lookup", new Document()
                        .append("from", "speaker")
                        .append("localField", "speaker")
                        .append("foreignField", "_id")
                        .append("as", "speakerInfo")
                ),

                // Splitting the speakers array into separate documents
                new Document("$unwind", "$speakerInfo"),


                // output
                new Document("$project", new Document()
                        .append("text", "$text")
                        .append("speech", "$speech")
                        .append("speakerName", "$speakerInfo.name")
                        .append("speakerId", "$speakerInfo._id")
                ),
                // output
                new Document("$project", new Document("title", 1)
                        .append("_id", 1)
                        .append("text", 1)
                        .append("speech", 1)
                        .append("speakerName", 1)
                        .append("speakerId", 1)
                )
        ));
    }

    /**
     * set the daten of main seite
     * @param limit
     * @return
     */

    @Override
    public List<Document> findByDateDescending(int limit) {
        this.meetingCollection = dbConnectionHandler.getCollection("meeting");
        return meetingCollection.find().sort(descending("date")).limit(limit).into(new ArrayList<>());

    }

    /**
     * save the new document from speaker(admin edit)
     * @param document
     * @return
     */

    @Override
    public ObjectId save(Document document) {
        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        speakerCollection.insertOne(document);
        return document.getObjectId("_id");
    }

    /**
     * update the data from speaker
     * @param id
     * @param fieldMap
     */

    @Override
    public void updateByFieldMap(ObjectId id, Map<String, Object> fieldMap) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        Document query = new Document("_id", id);
        Document updateDoc = new Document("$set", fieldMap);
        speakerCollection.updateOne(query, updateDoc);
    }

    /**
     * update the data from speaekr
     * @param id
     * @param fieldMap
     */

    @Override
    public void updateByFieldMap(String id, Map<String, Object> fieldMap) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        Document query = new Document("_id", id);
        Document updateDoc = new Document("$set", fieldMap);
        speakerCollection.updateOne(query, updateDoc);
    }

    /**
     * update speech by ID
     * @param id
     * @param json
     * @return
     */

    @Override
    public boolean updateSpeakerById(ObjectId id, String json) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        Document query = new Document("_id", id);
        Document update = new Document("$set", Document.parse(json));
        UpdateResult updateResult = speakerCollection.updateOne(query, update);
        return updateResult.getModifiedCount() > 0;
    }

    /**
     * search the speakers by given data
     * @return
     */
    @Override
    public List<Document> findAllSpeaker() {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        return speakerCollection.find().sort(descending("name")).into(new ArrayList<>());
    }

    /**
     * find the speaker by given id
     * @param id
     * @return
     */
    @Override
    public Document findSpeakerById(String id) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        return speakerCollection.find(eq("_id", id)).first();
    }

    /**
     * the aggregate result by speaker
     * @param id
     * @return
     */
    @Override
    public Document findByIdAggregate(String id) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");

        this.abgeordnterCollection = dbConnectionHandler.getCollection("Abgeordneter");
        Document speakerDocument = speakerCollection.find(new Document("_id", id)).first();
        Document abgeordnterDocument = abgeordnterCollection.find(new Document("_id", id)).first();

        if (abgeordnterDocument == null) {
            return speakerDocument;
        } else {
            speakerDocument.putAll(abgeordnterDocument);
            return speakerDocument;
        }
    }

    /**
     * search the speaker by given name ,firstname, fraction, party
     * @param name
     * @param firstName
     * @param fraction
     * @param party
     * @return
     */

    @Override
    public List<Document> searchSpeaker(String name, String firstName, String fraction, String party) {

        this.speakerCollection = dbConnectionHandler.getCollection("speaker");
        Document query = new Document();

        if (name != null && !name.isEmpty()) {
            query.append("name", new Document("$regex", name));
        }

        // Fuzzy query with firstName parameter
        if (firstName != null && !firstName.isEmpty()) {
            query.append("firstName", new Document("$regex", firstName));
        }

        // Adding fuzzy query conditions for the party parameter
        if (party != null && !party.isEmpty()) {
            query.append("party", new Document("$regex", party));
        }

        if (fraction != null && !fraction.isEmpty()) {
            query.append("fraction", new Document("$regex", fraction));
        }

        List<Document> speakers = speakerCollection.find(query).sort(descending("name")).into(new ArrayList<>());

        return speakers;
    }

    /**
     * search the data of speech
     * @return
     */
    @Override
    public AggregateIterable<Document> aggregate() {
        this.speechCollection = dbConnectionHandler.getCollection("speech");
        return speechCollection.aggregate(Arrays.asList(
                // First match the eligible speech records
                //new Document("$match", new Document("someCondition", "someValue")),

                // Associate the speaker field in speech to the speaker collection
                new Document("$lookup", new Document()
                        .append("from", "speaker")
                        .append("localField", "speaker")
                        .append("foreignField", "_id")
                        .append("as", "speakerInfo")
                ),

                // Splitting the speakers array into separate documents
                new Document("$unwind", "$speakerInfo"),
                        /*
                        new Document("$lookup", new Document()
                                .append("from", "comment")
                                .append("localField", "comments")
                                .append("foreignField", "_id")
                                .append("as", "commentInfo")
                        )
                        */


                // output
                new Document("$project", new Document()
                        .append("title", "$protocol.title")
                        .append("date", "$protocol.date")
                        .append("starttime", "$protocol.starttime")
                        .append("endtime", "$protocol.endtime")
                        .append("place", "$protocol.place")
                        .append("speakerName", "$speakerInfo.name")
                        .append("speakerId", "$speakerInfo._id")
                        .append("index", "$agenda.index")

                ),
                // output
                new Document("$project", new Document("title", 1)
                        .append("date", 1)
                        .append("starttime", 1)
                        .append("endtime", 1)
                        .append("place", 1)
                        .append("speakerName", 1)
                        .append("speakerId", 1)
                        .append("index", 1)
                )
        ));
    }

    /**
     * search the speech by timefilter
     * @param starttime
     * @param endtime
     * @return
     */
    @Override
    public AggregateIterable<Document> searchSpeech(Date starttime, Date endtime) {

        Document query = new Document();

        if (starttime != null) {
            query.append("protocol.starttime", new Document("$gte", starttime.getTime()));
        }

        if (endtime != null) {
            query.append("protocol.endtime", new Document("$lte", endtime.getTime()));
        }

        return aggregateQuery(query);
    }

    /**
     * query the title of speech
     * @param protocol
     * @return
     */

    @Override
    public AggregateIterable<Document> queryDownloadSpeeches(String protocol) {
        Document query = new Document("protocol.title", protocol);
        return downloadAggregateQuery(query);
    }

    /**
     * download the data by query
     * @param query
     * @return
     */

    @Override
    public AggregateIterable<Document> downloadAggregateQuery(Document query) {
        this.speechCollection = dbConnectionHandler.getCollection("speech");
        return speechCollection.aggregate(Arrays.asList(
                new Document("$match", query),


                new Document("$lookup", new Document()
                        .append("from", "speaker")
                        .append("localField", "speaker")
                        .append("foreignField", "_id")
                        .append("as", "speakerInfo")
                ),

                new Document("$unwind", "$speakerInfo"),
                        /*
                        new Document("$lookup", new Document()
                                .append("from", "comment")
                                .append("localField", "comments")
                                .append("foreignField", "_id")
                                .append("as", "commentInfo")
                        )
                        */

                new Document("$project", new Document()
                        .append("title", "$protocol.title")
                        .append("date", "$protocol.date")
                        .append("starttime", "$protocol.starttime")
                        .append("endtime", "$protocol.endtime")
                        .append("place", "$protocol.place")
                        .append("speakerName", "$speakerInfo.name")
                        .append("speakerId", "$speakerInfo._id")
                        .append("index", "$agenda.index")
                        .append("text", "$text")
                        .append("comments", "$comments")
                ),
                // output
                new Document("$project", new Document("title", 1)
                        .append("date", 1)
                        .append("starttime", 1)
                        .append("endtime", 1)
                        .append("place", 1)
                        .append("speakerName", 1)
                        .append("speakerId", 1)
                        .append("index", 1)
                        .append("text", 1)
                        .append("comments", 1)
                )
        ));
    }

    /**
     * aggregate search
     * @param query
     * @return
     */

    @Override
    public AggregateIterable<Document> aggregateQuery(Document query) {
        this.speechCollection = dbConnectionHandler.getCollection("speech");
        return speechCollection.aggregate(Arrays.asList(

                new Document("$match", query),


                new Document("$lookup", new Document()
                        .append("from", "speaker")
                        .append("localField", "speaker")
                        .append("foreignField", "_id")
                        .append("as", "speakerInfo")
                ),

                new Document("$unwind", "$speakerInfo"),
                        /*
                        new Document("$lookup", new Document()
                                .append("from", "comment")
                                .append("localField", "comments")
                                .append("foreignField", "_id")
                                .append("as", "commentInfo")
                        )
                        */
                new Document("$project", new Document()
                        .append("title", "$protocol.title")
                        .append("date", "$protocol.date")
                        .append("starttime", "$protocol.starttime")
                        .append("endtime", "$protocol.endtime")
                        .append("place", "$protocol.place")
                        .append("speakerName", "$speakerInfo.name")
                        .append("speakerId", "$speakerInfo._id")
                        .append("index", "$agenda.index")
                ),
                // output
                new Document("$project", new Document("title", 1)
                        .append("date", 1)
                        .append("starttime", 1)
                        .append("endtime", 1)
                        .append("place", 1)
                        .append("speakerName", 1)
                        .append("speakerId", 1)
                        .append("index", 1)
                )
        ));
    }

    /**
     * update the speech(admin edit)
     * @param id
     * @param json
     * @return
     */


    @Override
    public boolean updateSpeechById(String id, String json) {
        this.speechCollection = dbConnectionHandler.getCollection("speech");
        Document query = new Document("_id", id);
        Document update = new Document("$set", Document.parse(json));
        UpdateResult updateResult = speechCollection.updateOne(query, update);
        return updateResult.getModifiedCount() > 0;
    }

    /**
     * find speech by given id
     * @param id
     * @return
     */
    @Override
    public Document findSpeechById(String id) {
        this.speechCollection = dbConnectionHandler.getCollection("speech");


        Document result = speechCollection.aggregate(
                Arrays.asList(
                        match(eq("_id", id)), // Match by id
                        new Document("$project", new Document()
                                .append("title", "$protocol.title")
                                .append("speaker", "$speaker")
                                .append("starttime", "$protocol.starttime")
                                .append("endtime", "$protocol.endtime")
                                .append("place", "$protocol.place")
                                .append("text", "$text")
                                .append("comments", "$comments")
                                .append("length", "$length")
                        ),
                        // output
                        new Document("$project", new Document("_id", 1)
                                .append("title", 1)
                                .append("speaker", 1)
                                .append("text", 1)
                                .append("comments", 1)
                                .append("length", 1)
                                .append("starttime", 1)
                                .append("endtime", 1)
                                .append("place", 1)
                        )
                )
        ).first();

        return result;
    }

    /**
     * global search
     * @param keyword
     * @return
     */

    @Override
    public AggregateIterable<Document> globalQueryByKeyword(String keyword) {
        String regexKeyword = ".*" + Pattern.quote(keyword) + ".*";
        Document query = new Document("text", new Document("$regex", regexKeyword));
        return aggregateQuery(query);
    }

    /**
     * run the nlp analyse
     * @return
     */

    @Override
    public NLPHelper getNLPHelper() {
        return this.nlpHelper;
    }
}



