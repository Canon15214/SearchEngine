/*
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.
 */

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };
  private static String output;
  private static Map<String, String> parameters;
  private static Map<String, ScoreList> fbDocs = new HashMap<String, ScoreList>();


  //  --------------- Methods ---------------------------------------

  /**
   * @param args The only argument is the parameter file name.
   * @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    parameters = readParameterFile (args[0]);

    //  Configure query lexical processing to match index lexical
    //  processing.  Initialize the index and retrieval model.

    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

    Idx.initialize (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);
    output = parameters.get("trecEvalOutputPath");

    //  Perform experiments.   
    
    String queryFilePath = parameters.get("queryFilePath");
    if(parameters.containsKey("fb") && parameters.get("fb").equals("true")){
  	  int numFbDocs = Integer.parseInt(parameters.get("fbDocs"));
  	  int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
  	  double fbMu = Double.parseDouble(parameters.get("fbMu"));
  	  double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
  	  String fbExpanQueryFile = parameters.get("fbExpansionQueryFile");
  	  String fbInitialRankingFile = null;
  	  if(parameters.containsKey("fbInitialRankingFile"))
  		  fbInitialRankingFile = parameters.get("fbInitialRankingFile");
  	  else 
  		 processQueryFile(queryFilePath, model, true);
  	  QueryExpander.getQueryExpansion(fbDocs, queryFilePath, fbInitialRankingFile, numFbDocs, fbTerms, fbMu, fbOrigWeight, fbExpanQueryFile);
    }
    
    processQueryFile(queryFilePath, model, false);

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   * Allocate the retrieval model and initialize it using parameters
   * from the parameter file.
   * @return The initialized retrieval model
   * @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }
    else if (modelString.equals("rankedboolean")){
    	model = new RetrievalModelRankedBoolean();
    }
    else if (modelString.equals("bm25")) {
		double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
		double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
		double b = Double.parseDouble(parameters.get("BM25:b"));
		model = new RetrievalModelBM25(k_1, b, k_3);
	}
    else if (modelString.equals("indri")) {
		double mu = Double.parseDouble(parameters.get("Indri:mu"));
		double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
		model = new RetrievalModelIndri(mu, lambda);
	}
    else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }

    return model;
  }

  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException Error accessing the Lucene index.
   */
  static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

    //  Add a default query operator to every query. This is a tiny
    //  bit of inefficiency, but it allows other code to assume
    //  that the query will return document ids and scores.

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";

    //  Simple query tokenization.  Terms like "near-death" are handled later.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    //  This is a simple, stack-based parser.  These variables record
    //  the parser's state.
    
    Qry currentOp = null;
    Stack<Qry> opStack = new Stack<Qry>();
    boolean weightExpected = false;
    Stack<Double> weightStack = new Stack<Double>();

    //  Each pass of the loop processes one token. The query operator
    //  on the top of the opStack is also stored in currentOp to
    //  make the code more readable.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
        continue;
      } else if (token.equals(")")) {	// Finish current query op.

        // If the current query operator is not an argument to another
        // query operator (i.e., the opStack is empty when the current
        // query operator is removed), we're done (assuming correct
        // syntax - see below).

        opStack.pop();

        if (opStack.empty())
          break;

	// Not done yet.  Add the current operator as an argument to
        // the higher-level operator, and shift processing back to the
        // higher-level operator.

        Qry arg = currentOp;
        currentOp = opStack.peek();
        currentOp.appendArg(arg);
        
        //If a Weighted Op reaches its end, set the weight flag false.
        //EX. #or(#wand(0.3 apple 0.7 iphone) samsung)
        //When iphone is processed, weightExpected are set to true.
        //However, next token isn't a weight.
        if (arg instanceof QrySopWeighted){
        	weightExpected = false;
        }
        
        //Set weight for nested ops. EX. #wand(0.3 #wsum(a.title a.body) 0.7 b)
        if((currentOp instanceof QrySopWeighted) && !weightStack.isEmpty()){
      	  ((QrySop)arg).setWeight(weightStack.pop());
      	  weightExpected = true;
        }
        

      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QrySopOr ();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#and")) {
        currentOp = new QrySopAnd ();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
		currentOp = new QrySopSum ();
		currentOp.setDisplayName (token);
		opStack.push(currentOp);
	  } else if (token.equalsIgnoreCase("#wsum")) {
		currentOp = new QrySopWsum ();
		currentOp.setDisplayName (token);
		opStack.push(currentOp);
		weightExpected = true;
	  } else if (token.equalsIgnoreCase("#wand")) {
		currentOp = new QrySopWand ();
		currentOp.setDisplayName (token);
		opStack.push(currentOp);
		weightExpected = true;
	  } else if (token.toLowerCase().startsWith("#near")) {
    	if(Pattern.matches("#near/[1-9][0-9]*", token.toLowerCase())){
    	  String[] nearOp = token.split("/");
		  int dist = Integer.parseInt(nearOp[1]);
		  currentOp = new QryIopNear(dist);
    	  currentOp.setDisplayName (token);
    	  opStack.push(currentOp);
    	} else break;
      }else if (token.toLowerCase().startsWith("#window")) {
      	if(Pattern.matches("#window/[1-9][0-9]*", token.toLowerCase())){
      	  String[] windowOp = token.split("/");
  		  int windowSize = Integer.parseInt(windowOp[1]);
  		  currentOp = new QryIopWindow(windowSize);
      	  currentOp.setDisplayName (token);
      	  opStack.push(currentOp);
      	} else break;
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryIopSyn();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else {

    	  //A token can be an op, a term or a weight. A weight should come before a term, 
    	  //set weight flag true to indicate that next token is a weight rather than a number 
    	  //Once the weight has been push to a stack, weight flag should be set false to 
    	  //indicate next token is a term.
    	if(weightExpected){
    		try{
    			weightStack.push(Double.parseDouble(token));
    			weightExpected = false;
    		} catch (NumberFormatException e) {
    			throw new IllegalArgumentException
    			("Missing weight for " + opStack.peek().getDisplayName());
    		}
    	} else {
        //  Split the token into a term and a field.

        int delimiter = token.indexOf('.');
        String field = null;
        String term = null;

        if (delimiter < 0) {
          field = "body";
          term = token;
        } else {
          field = token.substring(delimiter + 1).toLowerCase();
          term = token.substring(0, delimiter);
        }

        if ((field.compareTo("url") != 0) &&
	    (field.compareTo("keywords") != 0) &&
	    (field.compareTo("title") != 0) &&
	    (field.compareTo("body") != 0) &&
            (field.compareTo("inlink") != 0)) {
          throw new IllegalArgumentException ("Error: Unknown field " + token);
        }

        //  Lexical processing, stopwords, stemming.  A loop is used
        //  just in case a term (e.g., "near-death") gets tokenized into
        //  multiple terms (e.g., "near" and "death").

        String t[] = tokenizeQuery(term);
        
        //Set weight for each argument of weighted ops
        //If the term is a stopword, weight would not be used.
        double w = Double.NaN;
        if((currentOp instanceof QrySopWeighted) && !weightStack.isEmpty()){
        	w = weightStack.pop();
        	weightExpected = true;
        }
        
        //#wsum( 0.7 bear 0.3 near-death) should be parsed as
        //#wsum (0.7 bear 0.3 near 0.3 death) rather than #wsum (0.7 bear 0.15 near 0.15 death)
        for (int j = 0; j < t.length; j++) {
        	
          Qry termOp = new QryIopTerm(t [j], field);
          currentOp.appendArg (termOp);     
          
          if(!Double.isNaN(w)){
        	  int i = currentOp.args.size()-1;
        	  ((QrySop)currentOp.args.get(i)).setWeight(w);   
          }
        }
      }
     }
    }


    //  A broken structured query can leave unprocessed tokens on the opStack,

    if (tokens.hasMoreTokens()) {
      throw new IllegalArgumentException
        ("Error:  Query syntax is incorrect.  " + qString);
    }

    return currentOp;
  }

  /**
   * Remove degenerate nodes produced during query parsing, for
   * example #NEAR/1 (of the) that can't possibly match. It would be
   * better if those nodes weren't produced at all, but that would
   * require a stronger query parser.
   */
  static boolean parseQueryCleanup(Qry q) {

    boolean queryChanged = false;

    // Iterate backwards to prevent problems when args are deleted.

    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i = q.args.get(i);

      // All operators except TERM operators must have arguments.
      // These nodes could never match.
      
      if ((q_i.args.size() == 0) &&
	  (! (q_i instanceof QryIopTerm))) {
        q.removeArg(i);
        queryChanged = true;
      } else 

	// All operators (except SCORE operators) must have 2 or more
	// arguments. This improves efficiency and readability a bit.
	// However, be careful to stay within the same QrySop / QryIop
	// subclass, otherwise the change might cause a syntax error.
	
	if ((q_i.args.size() == 1) &&
	    (! (q_i instanceof QrySopScore))) {
	
	  if(q_i instanceof QrySop)
		  ((QrySop)q_i.args.get(0)).setWeight(((QrySop)q_i).getWeight());
	  Qry q_i_0 = q_i.args.get(0);


	  if (((q_i instanceof QrySop) && (q_i_0 instanceof QrySop)) ||
	      ((q_i instanceof QryIop) && (q_i_0 instanceof QryIop))) {
	    q.args.set(i, q_i_0);
	    queryChanged = true;
	  }
	} else 

	  // Check the subtree.
	  
	  if (parseQueryCleanup (q_i))
	    queryChanged = true;
    }
    
    return queryChanged;
  }

  /**
   * Print a message indicating the amount of memory used. The caller
   * can indicate whether garbage collection should be performed,
   * which slows the program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    Qry q = parseQuery(qString, model);

    // Optimize the query.  Remove query operators (except SCORE
    // operators) that have only 1 argument. This improves efficiency
    // and readability a bit.

    if (q.args.size() == 1) {
      Qry q_0 = q.args.get(0);

      if (q_0 instanceof QrySop) {
    	  q = q_0;
      }
    }

    //Reduce duplicate arguments for BM25 SUM operator
    if(model instanceof RetrievalModelBM25 && q instanceof QrySopSum){
    	//<qry,argument_index>
    	Map<Qry,Integer> truncArgs = new HashMap<Qry,Integer>();
    	//truncate duplicate arguments of SUM and modify the responding qtf.
    	for (int i = 0; i < q.args.size(); i++){
    		Qry q_i = q.args.get(i);
    		//Only consider "terms" aka QryIops
    		if(!(q_i instanceof QrySopScore))
    			continue;
    		Qry q_iop = q_i.args.get(0);
    		if(!truncArgs.containsKey(q_iop)){
    			truncArgs.put(q_iop, i);
    		} else {
    			int j = truncArgs.get(q_iop);
    			Qry qj = q.args.get(j);
    			qj.setQtf(qj.getQtf()+1);
    			q.args.remove(i);
    		}
    	}
    }
    
    while ((q != null) && parseQueryCleanup(q))
      ;

    // Show the query that is evaluated

    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return r;
    } else
      return null;
  }

  /**
   * Process the query file.
   * @param queryFilePath
   * @param model
   * @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath, RetrievalModel model, boolean fbPreprocess) throws IOException {

    BufferedReader input = null;
    BufferedReader expansion = null;
    try {
      String qLine = null;
      String eqLine = null;
      input = new BufferedReader(new FileReader(queryFilePath));
      
      
      Map<String, String> queryExpansions = new HashMap<String, String>();
      if(parameters.containsKey("fb") && parameters.get("fb").equals("true") && !fbPreprocess) {
    	  expansion = new BufferedReader(new FileReader(parameters.get("fbExpansionQueryFile")));
    	  while ((eqLine = expansion.readLine()) != null) {
    	        int d = eqLine.indexOf(':');
    	        String eqid = eqLine.substring(0, d);
    	        String equery = eqLine.substring(d + 1);
    	        queryExpansions.put(eqid, equery);
    	  } 
      }
      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);
        
        System.out.println("Query " + qLine);

        if(parameters.containsKey("fb") && parameters.get("fb").equals("true") && !fbPreprocess) {
        	double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
        	String qexpansion = queryExpansions.get(qid);
        	query = "#wand ( " + fbOrigWeight + " #and ( " + query + " ) "
		    		 + (1.0-fbOrigWeight) + " "	+ qexpansion + " ) ";
        	System.out.println("Expanded Query " + query);
        }
        
        ScoreList r = null;

        r = processQuery(query, model);

        if (r != null && !fbPreprocess) {
          r.sort();
          printResults(qid, r);
          System.out.println();
        } else if(r !=null && fbPreprocess) {
          r.sort();
          r.truncate(Integer.valueOf(parameters.get("fbDocs")));
          fbDocs.put(qid, r);
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
      if(expansion != null)
    	  expansion.close();
    }
  }
  
  
  /**
   * Print the query results.
   * OUTPUTS FORMAT IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result) throws IOException {
	
	BufferedWriter bw  = new BufferedWriter(new FileWriter(output,true));	
	final String QRY_CONS = "Q0";
	final String EXP_IDENTIFIER = "fubar";
	final int BEST_K_DOCS = 100;
    if (result.size() < 1) {
    	System.out.print(queryName + " " + QRY_CONS + " dummy 1 0 " + EXP_IDENTIFIER + "\n");
    	bw.append(queryName + " " + QRY_CONS + " dummy 1 0 " + EXP_IDENTIFIER + "\n");
    } else {
      int numOfResults = Math.min(BEST_K_DOCS,result.size());
      for (int i = 0; i < numOfResults; i++) {
    	System.out.print(queryName + " " + QRY_CONS + " " + Idx.getExternalDocid(result.getDocid(i)) + " " 
          		+ (i+1) + " " + result.getDocidScore(i) + " " + EXP_IDENTIFIER + "\n");
        bw.append(queryName + " " + QRY_CONS + " " + Idx.getExternalDocid(result.getDocid(i)) + " " 
        		+ (i+1) + " " + result.getDocidScore(i) + " " + EXP_IDENTIFIER + "\n");
      }
    }
    bw.close();
  }

  /**
   * Read the specified parameter file, and confirm that the required
   * parameters are present.  The parameters are returned in a
   * HashMap.  The caller (or its minions) are responsible for
   * processing them.
   * @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query
   *          String containing query
   * @return Array of query tokens
   * @throws IOException Error accessing the Lucene index.
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }

}
