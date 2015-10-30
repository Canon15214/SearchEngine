/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The WSUM operator for all retrieval models.
 *  SUM operator adds up each term's score for each document
 *	The actual input of #SUM must be a score list (if not, make it one)
 */
public class QrySopWsum extends QrySopWeighted {
	
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
	  public boolean docIteratorHasMatch (RetrievalModel r) {
		  if(r instanceof RetrievalModelIndri)
			  return this.docIteratorHasMatchMin(r);
		  else throw new IllegalArgumentException
	      (r.getClass().getName() + " doesn't support the WSUM operator.");
	  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  Same implementation for all retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  @Override
  public double getScore (RetrievalModel r) throws IOException {
	if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri (r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the WSUM operator.");
    }
  }

	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri (RetrievalModel r) throws IOException{
		  double score = 0.0;
		    if (this.docIteratorHasMatchCache()) {
		    	int docId = this.docIteratorGetMatch();
		    	//Sum operator combines score by adding them up
		    	for(Qry arg : this.args){
		    		double nw = ((QrySop)arg).getWeight() / argsWeightSum;
		    		if(arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docId){		    			
		    			score += nw * ((QrySop) arg).getScore(r);	    		
		    		} else {
		    			score += nw * ((QrySop) arg).getDefaultScore(r, docId);
		    		}
				}
		    }
		    return score;
	}

	/**
	 *  Support for Indri best match model. Combine scores for n items.
	 *  @param r A retrieval model that guides initialization
	 *  @param docid The specific doc id associated with the score
	 *  @throws IOException Error accessing the Lucene index.
	 */
	@Override
	public double getDefaultScore (RetrievalModel r,int docId) throws IOException {
			if (r instanceof RetrievalModelIndri){
				double score = 0.0;
				for(Qry arg : this.args){
					double nw = ((QrySop)arg).getWeight() / argsWeightSum;
					score += nw * ((QrySop)arg).getDefaultScore(r, docId);
				}
				return score;
			} else {
				throw new IllegalArgumentException
				("No support for Default Score in " + r.getClass().getName());
			}
	}
}
