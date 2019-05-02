package CS483Project.Watson;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class Lemmatizer {
	public Properties props;
	public StanfordCoreNLP pipeline;
	
	public Lemmatizer() {
        // Create StanfordCoreNLP object properties, with POS tagging
        // (required for lemmatization), and lemmatization
        props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        pipeline = new StanfordCoreNLP(props);        
	}
	
	/**
	 * Tokenize & Lemmatize all text in the document and concatenate all
	 * tokens to a string.  The returned string contains each lemmatized
	 * token from the document text separated by a whitespace.
	 */
	public String lemmatize(Annotation doc) {
        String retStr = "";
        String word = "";
        
        // Run all specified annotators on this document's text
        pipeline.annotate(doc);
        
        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        
        for (CoreMap sentence : sentences) {
          for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
        	  word = token.getString(LemmaAnnotation.class);
        	  retStr += (word + " ");
          }
        }
        
        return retStr;
	}
}
