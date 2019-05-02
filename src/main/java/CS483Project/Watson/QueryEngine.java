package CS483Project.Watson;

import org.apache.lucene.analysis.TokenStream;

/*
 * Matthew Theisen, NetID: theisenm
 * CS483 Spring 2019
 */

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.*;
import edu.stanford.nlp.util.CoreMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Scanner;

@SuppressWarnings("deprecation")
public class QueryEngine {
    Directory index;
    StandardAnalyzer analyzer;
    HashMap<String, String[]> questions;

    public QueryEngine(){      
        // Specify analyzer
        analyzer = new StandardAnalyzer();
        
        //buildIndex();  Index has been written to disk already
        questions = mapQuestions();
    }

    

    public static void main(String[] args ) {  	
    	QueryEngine queryEngine = null;
    	try {          
            queryEngine = new QueryEngine();         
        }
        catch (Exception ex) {
        	ex.printStackTrace();
        }
    	
    	IndexReader reader = null;
    	IndexSearcher searcher = null;
    	
    	// Open the index for searching
    	try {
    		queryEngine.index = FSDirectory.open(Paths.get(".\\LuceneIndex"));
    		reader = DirectoryReader.open(queryEngine.index);
    		searcher = new IndexSearcher(reader);		
    	} catch (IOException e) {
    		System.out.println("Couldn't open .\\LuceneIndex\\ folder.");
    		e.printStackTrace();
    	} catch (NullPointerException e) {
    		System.out.println("null pointer");
    		e.printStackTrace();
    	}
    	
    	
    	// BM25 / / / / / / /
    	// Tally correct answers from all questions
    	int numberCorrectBM25 = 0;
    	
    	for (String question : queryEngine.questions.keySet()) {
    		numberCorrectBM25 += queryEngine.findBestBM25(question, searcher);
    	}
    	// END BM25 / / / / /
    	
    	
    	
		// TF-IDF / / / / / 
		searcher.setSimilarity(new ClassicSimilarity());
    	int numberCorrectTFIDF = 0;
    	
    	// TF-IDF / / / / /
    	for (String question : queryEngine.questions.keySet()) {
    		numberCorrectTFIDF += queryEngine.findBestTFIDF(question, searcher);
    	}

    	// Display number correctly answered
    	System.out.println("\n\n\nNUMBER CORRECT (BM25): " + numberCorrectBM25 + " / 100");
    	System.out.println("\nNUMBER CORRECT (TF-IDF): " + numberCorrectTFIDF + " / 100");
    	
    	
    	try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    
    public int findBestTFIDF(String clue, IndexSearcher searcher) { 

    	int answeredCorrectly = 0;
    	String expectedAnswer = "", myAnswer = "";
    	
    	expectedAnswer = this.questions.get(clue)[1].toLowerCase();
    	String query = this.questions.get(clue)[1];  
    	// Lucene had trouble parsing exclamation marks
    	
    	System.out.println("\nOriginal Clue = "+clue);
    	System.out.println("Query to parse: "+query);
    	System.out.println("expected answer: "+expectedAnswer +"\n");
    		   	
   		   	
    	Query q = null;

    	try {
    		q = new QueryParser("docText", this.analyzer).parse(query);
    		
        	int hitsPerPage = 1;
        	
        	TopDocs docs = searcher.search(q, hitsPerPage);        	
        	ScoreDoc[] hits = docs.scoreDocs;
        	        	
        	System.out.println("Top " + hits.length + " hits.");
        	
        	// Find answer in top n hits -- use p@1 by default
        	// n = hitsPerPage
        	for(int i=0; i<hits.length; ++i) {
        	    int docId = hits[i].doc;
        	    Document d = searcher.doc(docId);
        	    System.out.println((i + 1) + ". " + d.get("docid"));
        	    
        	    // myAnswer = top result
        	    if (i == 0) {myAnswer = d.get("docid");}       	    
        	}
      	
        	if (expectedAnswer.toLowerCase().equals(myAnswer.toLowerCase())) {answeredCorrectly = 1;}
        	       	
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return answeredCorrectly;
    }
    
    /**
     * Search the index to find the best estimated answer for the clue using BM25
     * 
     * @param clue Jeopardy question to be answered
     * 
     * @return answer
     */
    public int findBestBM25(String clue, IndexSearcher searcher) {
    	
    	int answeredCorrectly = 0;
    	String clueLemmas ="", expectedAnswer = "", myAnswer = "";

    	clueLemmas = this.questions.get(clue)[0];   		
    	expectedAnswer = this.questions.get(clue)[1].toLowerCase();
    	
    	System.out.println("\nOriginal Clue = "+clue);
    	System.out.println("Lemmatized Clue = " + clueLemmas);
    	System.out.println("expected answer: "+expectedAnswer+"\n");
    		   	
    	Query q = null;
    	String querystr = clueLemmas;
    	try {
    		q = new QueryParser("docText", this.analyzer).parse(querystr);
    		
        	int hitsPerPage = 3;
        	
        	TopDocs docs = searcher.search(q, hitsPerPage);        	
        	ScoreDoc[] hits = docs.scoreDocs;
        	        	
        	System.out.println("Top " + hits.length + " hits.");
        	
        	// Find answer in top n hits -- use p@1 by default
        	// n = hitsPerPage
        	for(int i=0; i<hits.length; ++i) {
        	    int docId = hits[i].doc;
        	    Document d = searcher.doc(docId);
        	    System.out.println((i + 1) + ". " + d.get("docid"));
        	    
        	    // myAnswer = top result
        	    if (i == 0) {myAnswer = d.get("docid");}
        	    
        	    // Below line used for determining if answer occurs in top n hits, not p@1
        	    //else if (expectedAnswer.toLowerCase().equals(d.get("docid").toLowerCase())) {myAnswer = d.get("docid");}
        	    
        	}

        	
        	// Print top 10 hits
        	/*
        	for(int i=0; i<hits.length; ++i) {
        	    int docId = hits[i].doc;
        	    Document d = searcher.doc(docId);
        	    System.out.println((i + 1) + ". " + d.get("docid"));
        	    
        	    // myAnswer = top result
        	    if (i == 0) {myAnswer = d.get("docid");}
        	    
        	}*/
        	
        	if (expectedAnswer.toLowerCase().equals(myAnswer.toLowerCase())) {answeredCorrectly = 1;}
        	       	
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return answeredCorrectly;
    }
    
    
    /**
     * Read questions file to map all clues to answers
     * 
     * @return HashMap<clue, answer>
     */
    public HashMap<String, String[]> mapQuestions() {
    	// Build hashmap of <clue:[lemmatized clue,answer]> for all questions
    	File questions = new File(".\\src\\main\\java\\CS483Project\\Watson\\questions.txt");
		HashMap<String, String[]> qa = new HashMap<String, String[]>();
		String line = "", category = "", clue = "", answer = "";
		
		try {
			Scanner scan = new Scanner(questions);
        	int countLines = 0;
        	while (scan.hasNextLine()) 
            {
        		line = scan.nextLine();
        		countLines++;
        		if (countLines == 1) {category = line;}
        		else if (countLines == 2) {clue = line;}
        		else if (countLines == 3) {answer = line;}
        		else { // This is the newline
        			
        			// Lemmatize clue, add to question map
        			Lemmatizer l = new Lemmatizer();
        			
        			// Generate COREnlp annotation
    				Annotation doc = new Annotation(clue);
    				
    				// Tokenize and lemmatize clue
    				String lemmatizedClue = l.lemmatize(doc);
    				
    				String temp[] = lemmatizedClue.split(" ");
    				String temp2 = "";
    				
    				// Rather than running the lemmatized clue through Lucene analyzer,
    				// I will just eliminate single-character and two-character lemmas.
    				for (int i = 0; i < temp.length; i++) {
    					if (temp[i].length() > 2) {
    						temp2 += (temp[i] + " ");
    					}
    				}
    				temp = new String[] {temp2,answer};

    				
        			qa.put(clue, temp);
    				// qa map looks like: <CLUE,[Lemmatized Clue, ANSWER]>
        			
        			countLines = 0;
        		}
            }
        	
            scan.close();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }          
		return qa;
    }
    

    /** 
     * Build the Lucene index
     */
    private void buildIndex() {
        // Write index to disk
        try {
        	index = FSDirectory.open(Paths.get(".\\LuceneIndex"));
        } catch (IOException e) {
        	System.out.println("Failed to create index folder");
        	e.printStackTrace();
        }
        
        // Create IndexWriter
        IndexWriterConfig config = new IndexWriterConfig(analyzer);       
        IndexWriter writer = null;
		try {
			writer = new IndexWriter(index, config);
		} catch (IOException e) {
			e.printStackTrace();
		}

        // Parse each Wiki file from resources folder
		File dir = new File(".\\src\\main\\resources\\");
	    
	    File[] fileNames = dir.listFiles();   
	    Lemmatizer lemmatizer = new Lemmatizer();
	    
		for (File f : fileNames) 
		{
			System.out.println("Indexing file: "+f.getName());
			parseTextFile(f, writer, lemmatizer);
		}
		
		try {
			writer.close();
			index.close();
		} catch (IOException e) {
        e.printStackTrace();
		}          
		
		

}
     
        
 
    
    /**
     * Parse a single file for docIDs associated text. 
     * Add the document to the index.
     * 
     * @param f file to be read
     * @param w Lucene IndexWriter
     * @param lemmatizer Class containing COREnlp functionality
     */
    private void parseTextFile(File f, IndexWriter w, Lemmatizer lemmatizer) {
        try {
        	Scanner inputScanner = new Scanner(f);
        	String line;
        	String bracketedText;
        	Annotation doc = null;
        	String currentDocID = null;
        	String docText = "";
        	String lemmatizedText = "";
        	boolean firstDocFoundAlready = false;
        	
        	while (inputScanner.hasNextLine()) 
            {
            	// Get current line of file
            	line = inputScanner.nextLine();
            	
            	// Skip blank lines, or lines <2 chars
            	if (line.isEmpty() || line.length() < 2) {
            		continue;
            	}
            	     
            	// Enter this statement if current line is "[[.....]]"
            	if (line.charAt(0) == '[' && line.charAt(1) == '[' &&
            			line.charAt(line.length()-1) == ']' && line.charAt(line.length()-2) == ']') {
            		
            		// Get the text between double-brackets
            		bracketedText = line.substring(2,line.length()-2).toLowerCase();
            		
            		// Handle Wiki markup detection
            		if (bracketedText.startsWith("file:") || bracketedText.startsWith("image:") || bracketedText.startsWith("media:")) {
            			// This is not an article title, it is Wiki markup - ignore this line
            			continue;
            			
            		}
            		else {
            			// This is an article title.  All text that follows until
            			// the next docID is found are terms in this docID.  If we
            			// already found a previous docID in this file, add all terms concatenated
            			// so far to that previous docID in the index and reset the concat string.
            			if (firstDocFoundAlready) {            				
            				// Generate COREnlp annotation
            				doc = new Annotation(docText);
            				
            				// Tokenize and lemmatize document text
            				lemmatizedText = lemmatizer.lemmatize(doc);
            				
            				// Add document to index using Lucene analyzer
            				addDoc(w, currentDocID, lemmatizedText);      				
            			}
            			firstDocFoundAlready = true;
            			currentDocID = bracketedText;
            			docText = "";
            			continue;
            		}           		
            	}
                          
                // Concatenate all terms in this line to the document text
                docText += line;
                             
            } // File is finished   
        	
        	// Process final docID and text encountered and to index
        	doc = new Annotation(docText);
        	lemmatizedText = lemmatizer.lemmatize(doc);
        	addDoc(w, currentDocID, lemmatizedText);
        	
            inputScanner.close();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }          
    }
    

    /**
     * Add document to Lucene index
     * 
     * @param w Lucene IndexWriter
     * @param docid Document ID
     * @param docText Text of document, has gone through COREnlp processing and tokenization
     * 
     * @throws IOException
     */
    private static void addDoc(IndexWriter w, String docid, String docText) throws IOException
    {
    	  Document doc = new Document();
    	  doc.add(new TextField("docText", docText, Field.Store.YES));
    	  doc.add(new StringField("docid", docid, Field.Store.YES));
    	  w.addDocument(doc);
    }
}