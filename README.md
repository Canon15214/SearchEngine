This is a small ad-hoc search engine indexed with Lucene. 

##System Overview:
A single retrieval is a loop over a set of queries.

For each query:

1. Read one query from the query file.
2. Parse the query to create a corresponding query tree in which the internal nodes are query operators and the leaves are index terms. An example is shown below.  
  ![](http://boston.lti.cs.cmu.edu/classes/11-642/HW/HW1/QueryTree.gif)  
QUERY: #OR (#AND (neko case) blacklisted) 

    The query parser can handle the following cases:
If a query has no explicit query operator, default to #OR;  
If a query term has no explicit field (see below), default to 'body'; and  
Provide lexical processing (including stemming and discarding stopwords) of query terms using the provided tokenizeQuery method in the instructional code.  
4. Evaluate the query, using a depth-first strategy. The evaluation of a leaf node should fetch the inverted list for the index term, if one is available. Note that some query terms may not occur in the index.  
5. Sort the matching documents by their scores, in descending order. The external document id should be a secondary sort key (i.e., for breaking ties). Smaller ids should be ranked higher (i.e. ascending order).  
6. The best 100 documents retrieved will be written to a file in trec_eval format (shown below).  

QueryID   Q0	          DocID	             Rank	Score	  RunID  
For example:  
10	      Q0	  clueweb09-enwp03-35-1378  	1	   16	    run-1  
10	      Q0	  clueweb09-enwp00-78-1360  	2	   11	    run-1  
:	:	:	:	:	:  
11	      Q0	  clueweb09-enwp00-63-1141	  1	   18	    run-1  

The QueryID correspond to the query ID of the query you are evaluating. Q0 is a required constant. The DocID is the external document ID. The scores are in descending order, to indicate that how the results are ranked. The Run ID is an experiment identifier.  

###Data Structure  
The **InvList** class provides a very simple implementation of an inverted list. InvList supports field-based retrival in which a term matches only against the specified portion of a document. The field name (if any) is specified in the query using a simple suffix-based syntax of the form 'term.field', as in 'apple.title'. Each InvList object corresponds to a certain term with field identifier. A InvList object store the document posting of the term(Vector<DocPosting>) as well as some statistical information such as Corpus Term Frequency and Document Frequency. Inside a DocPosting class, we record a list of positions where the term occur in a document and Term Frequency in this document.  

The **ScoreList** class provides a very simple implementation of a score list. ScoreList maintains a list of ScoreListEntry. ScoreListEntry is a utility class to create a <internalDocid, externalDocid, score> object.  

###Query Operator
There are two kinds of query operators. QryIop(e.g. #TERM,#NEAR) produce new inverted list inside the class according to the operation it poses on the operands. For example, #TERM(cheap) reads the inverted list of term cheap from lucene index; #NEAR/1(#TERM(cheap) #TERM(internet)) combine two inverted list but filter out those documents in which the two terms are not adjacent. QrySop(e.g.#SCORE,#AND) produce score list. Qry is an abstract class for both QryIop and QrySop. Each type of query operator extends either QryIop or QrySop with a subclass (e.g., QrySopAnd). This implementation contains 6 query operators:

The **Term** operator, which just fetches an inverted list from the index and store as a InvList object inside;  
The **Syn** operator, which combines inverted lists;  
The **Near** operator, which produce inverted lists containing only proximite terms;  
The **Score** operator, which converts an inverted list into a score list;  
The **And** operator, which combines score lists;and  
The **Or** operator, which combines score lists.  
  
It is easy to extends and write new operators. Specify evaluate function to populate inverted lists for each inverted list operator; override getScore function to obtain score for each socre list operator. QryIop return populated inverted lists and QrySop return score for a document.  

Note, #Term and #Score are implicit operators, which means you don't need to include them explicitly in the query. Query parser will handle that when generating query tree. When processing query, #TERM fetch a term's inverted list; #SCORE transform every inverted list into a socre list; #AND takes intersection on the ids in the score lists, and result is another score list.  

###Retrieval Model
This software implements an unranked and ranked Boolean retrieval model. It is easily extended to other retrieval models. For example, to implement the Indri retrieval model, do the following.

+ Modify the QrySopScore to calculate a query likelihood score with Dirichlet smoothing.

+ Modify the query operators that operate on score lists (e.g., And) to implement the Indri score combinations.

If you want to support several retrieval models with the same software (e.g., ranked Boolean, Okapi BM25, Indri), create different subclasses for different models, e.g., RetrievalModelRankedBoolean, RetrievalModelBM25, RetrievalModelIndri, etc. A parameter setting or a flag in the query parser instructs it which subclasses to use when creating query trees.  

###Project Milestone
**Milestone1 09/22/2015**  
Right now I've finish a system which can support a Boolean retrieval. I will continue implementation of more retrival models such as BM25, Indri.  
**1. Query Operators:** AND, OR, NEAR/n  
**2. Retrieval Models:** RankedBoolean, UnrankedBoolean  
**3. Query Parser:**  
Natural Language Processing  
-Tokenization  
-Stemming  
-Stopwords Removal  
Building query tree using stack    

##Environment:
The application must run under Java version 1.8. The version of lucene is 4.3.0 or above.

##How to run:
QryEval is the main class. You must specify key parameters in a text file and pass it to the main class before you run the application. The params are written in the form of key-value pair(index=path_to_index). You should at least specify the following parameters.

**queryFilePath:** The path to your query file, which should be a text file containing multiple queries. Each line in the file contains one query.  
**indexPath:** The path to lucene index files.  
**trecEvalOutputPath:** The path to output file.  
**retrievalAlgorithm:** The name of retrieval model that search engine would apply. Right now the system only support "RankedBoolean" and "UnrankedBoolean".   

##Performance Test:
####Dataset: 
The corpus is 553,202 documents from the ClueWeb09 dataset(collected in January and February 2009 by Language Technologies Institute at Carnegie Mellon University). The corpus was indexed with Lucene.  
####Evaluation: 
The results are evaluated by expected reciprocal rank(ERR) and standard measures like P@10 and MAP, using [trec_eval](http://trec.nist.gov/trec_eval/). 
I use two different approach to construct the query.  

######Bag-of-words (BOW) with #OR:   
Use only the default #OR operator for each query, e.g. #OR(cheap internet). This is the "high Recall" strategy. 

                    | p@10 | P@20 | P@30 |  MAP | Running Time |
--------------------|------|------|------|------|--------------|
**UnrankedBoolean** |0.0100|0.0050|0.0033|0.0010|     14s      |
**RankedBoolean**   |0.1500|0.1800|0.1667|0.0566|     14s      |
######Bag-of-words (BOW) with #AND:  
Use only the provided #AND operator for each query, e.g. #AND(cheap internet). This is the "high Precision" strategy.  

                    | p@10 | P@20 | P@30 |  MAP | Running Time |
--------------------|------|------|------|------|--------------|
**UnrankedBoolean** |0.0200|0.0200|0.0433|0.0142|     2s       |
**RankedBoolean**   |0.2500|0.2600|0.2767|0.0980|     2s       |
