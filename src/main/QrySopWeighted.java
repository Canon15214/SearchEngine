import java.io.IOException;


public abstract class QrySopWeighted extends QrySop {
	//The total argument weights of this operator
	protected double argsWeightSum = 0.0;
	
	  /**
	   *  Initialize the query operator (and its arguments), including any
	   *  internal iterators.  If the query operator is of type QryIop, it
	   *  is fully evaluated, and the results are stored in an internal
	   *  inverted list that may be accessed via the internal iterator.
	   *  @param r A retrieval model that guides initialization
	   *  @throws IOException Error accessing the Lucene index.
	   */
		@Override
	  public void initialize(RetrievalModel r) throws IOException {
	    for (Qry q_i: this.args) {
	      q_i.initialize (r);
	      argsWeightSum += ((QrySop)q_i).getWeight();
	    }
	  }
}
