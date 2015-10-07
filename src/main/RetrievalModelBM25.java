/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM25
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
	
	//params for BM25 model
	private double k_1;
	private double k_3;
	private double b;

	public RetrievalModelBM25(double k_1, double b, double k_3){
	  this.k_1 = k_1;
	  this.k_3 = k_3;
	  this.b = b;
	}
	
	public String defaultQrySopName () {
		return new String ("#sum");
	}
	
	public double getK_1(){
		return k_1;
	}
	
	public double getK_3(){
		return k_3;
	}
	
	public double getB(){
		return b;
	}
}
