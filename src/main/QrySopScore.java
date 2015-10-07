/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {
	/*
	 * Store basic corpus statistics in order to reduce IO operations.
	 */
	String field = null;
	long corpuslen = 0;
	long fieldDocs = 0;
	long N = 0;
	
  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  @Override
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    }
    else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean (r);
    } 
    else if (r instanceof RetrievalModelBM25) {
        return this.getScoreBM25 (r);
    } 
    else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri (r);
    } 
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model, every document that matches the query gets a score of 1
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  
  /**
   *  getScore for the Ranked retrieval model, which is its term frequency (tf) in the document.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  QryIop q = (QryIop) this.args.get(0);
	  return (double) q.docIteratorGetMatchPosting().tf;
  }
  
	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException{
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			int docid = this.docIteratorGetMatchCache();
			QryIop q = (QryIop) this.args.get(0);
			String field = q.getField();
			
			
			//model parameters
			double k_1 = ((RetrievalModelBM25)r).getK_1();
			double k_3 = ((RetrievalModelBM25)r).getK_3();
			double b = ((RetrievalModelBM25)r).getB();
			
			//corpus statistics		
			double df = (double) q.getDf();
			double tf = (double) q.docIteratorGetMatchPosting().tf;
			double doclen = (double) Idx.getFieldLength(field, docid);
			double avg_doclen = ((double) corpuslen) / ((double) fieldDocs);
			double qtf = (double) q.getQtf();
					
			//idf
			double idf = Math.max(0, Math.log(((double)N - df + 0.5) / (df + 0.5)));
			//tf weight
			double tf_weight = tf / (tf + k_1*(1 - b + (b * doclen / avg_doclen)));
			//user weight
			double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
			
			return idf * tf_weight * user_weight;
		}
	}

	/**
	 *  getScore for the Indri retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		int docid = docIteratorGetMatchCache();
		return computeQueryLikelyhood(r, docid);
	}	
	
	  /**
	   *  Support for Indri best match model. Call this method when the document 
	   *  does not have a exact match for a term.
	   *  @param r A retrieval model that guides initialization
	   *  @param docid The specific doc id associated with the score
	   *  @throws IOException Error accessing the Lucene index.
	   */
	@Override
	public double getDefaultScore (RetrievalModel r,int docid) throws IOException {
		if (r instanceof RetrievalModelIndri){
			return computeQueryLikelyhood(r, docid);
		} else {
			throw new IllegalArgumentException
			("No support for Default Score in " + r.getClass().getName());
		}
	}
	

	/**
	 *  Get Default Score for the Indri retrieval model. Provide different entry for indri score.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double computeQueryLikelyhood(RetrievalModel r, int docid) throws IOException{
		QryIop q = (QryIop) this.args.get(0);		
		
		//model parameters
		double mu = ((RetrievalModelIndri)r).getMu();			
		double lambda = ((RetrievalModelIndri)r).getLambda();
			
		//corpus statistics
		double doclen = (double) Idx.getFieldLength(field, docid);
		double cp = (double) q.getCtf() / (double) corpuslen ;
		double tf = (docIteratorHasMatch(r) && docid == docIteratorGetMatchCache()) ?
				q.docIteratorGetMatchPosting().tf : 0.0;
		
		//calculation
		return (1 - lambda) * (tf + mu * cp) / (doclen + mu) + lambda * cp;
	}
	
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException{
    QryIop q = (QryIop) this.args.get (0);
    q.initialize (r);
    field = q.getField();
    corpuslen = Idx.getSumOfFieldLengths(field);
    N = Idx.getNumDocs();
    fieldDocs = Idx.getDocCount (field);
  }

}
