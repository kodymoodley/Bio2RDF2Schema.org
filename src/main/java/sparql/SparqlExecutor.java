package sparql;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

/**
 * 
 * @author Kody.Moodley
 * 1. Map Schema.org terms to Drugbank RDF terms using SPARQL construct
 * 2. Generate JSON-LD of Drugbank content marked up with Schema.org terms
 * 3. Generate HTML pages with the above JSON-LD embedded
 */

public class SparqlExecutor {
	
	public static final String HTML_template_prefix = "<!DOCTYPE html>\r\n" + 
			"<html lang=\"en\">\r\n" + 
			"	<head>\r\n" + 
			"  		<meta charset=\"UTF-8\">\r\n" + 
			"  		<title>Bio2RDF - Drugbank</title>\r\n" + 
			"		<link href=\"images/bio2rdf.png\" rel=\"shortcut icon\" />\r\n" + 
			"		<link rel=\"stylesheet\" type=\"text/css\" href=\"css/style.css\" media=\"screen\" />\r\n" + 
			"  		<script src=\"https://code.jquery.com/jquery-3.2.1.min.js\"></script>\r\n" + 
			"  		<script src=\"handlebars-v4.0.10.js\"></script>\r\n" + 
			"  		<!--<script src=\"https://cdnjs.cloudflare.com/ajax/libs/handlebars.js/4.0.10/handlebars.runtime.min.js\"></script>-->\r\n" + 
			"	\r\n" + 
			"		<script type=\"application/ld+json\">";
	
	public static final String HTML_template_midfix = "</script>\r\n" + 
			"	</head>\r\n" + 
			"\r\n" + 
			"	<body>\r\n" + 
			"		<br/><br/>\r\n" + 
			"		\r\n" + 
			"		<div id=\"all\">\r\n" + 
			"	 		<div id=\"main\" style=\"text-align: center;\">\r\n" + 
			"	 			<div style=\"height: 20%;\">&nbsp;</div>\r\n" + 
			"	 			<img alt=\"Drugbank\" src=\"images/drugbank.png\" />\r\n" + 
			"	 			<div id=\"main\" style=\"text-align: center;\">\r\n" + 
			"	 				<em>Bio2RDF Drugbank Data</em>\r\n" + 
			"				</div>\r\n" + 
			"\r\n" + 
			"				<br/><br/>\r\n" + 
			"\r\n" + 
			"			</div>\r\n" + 
			"\r\n" + 
			"			<div id=\"contents\">\r\n" + 
			"				<script type=\"text/javascript\">\r\n" + 
			"\r\n" + 
			"				var templateCode = \"<table><tbody><tr><td class='dataframeKey'>Drug Name</td><td>{{name}}</td></tr><tr><td class='dataframeKey'>Alternate Name</td><td>{{alternateName}}</td></tr><tr><td class='dataframeKey'>Manufacturer</td><td>{{manufacturer}}</td></tr><tr><td class='dataframeKey'>Dosage Schedule</td><td>{{doseSchedule}}</td></tr><tr><td class='dataframeKey'>Legal Status</td><td>{{legalStatus}}</td></tr><tr><td class='dataframeKey'>Mechanism of Action</td><td>{{mechanismOfAction}}</td></tr><tr><td class='dataframeKey'>URL</td><td><a href='{{schema:url}}'>{{schema:url}}</a></td></tr></tbody></table>\";\r\n" + 
			"				var template = Handlebars.compile(templateCode);\r\n" + 
			"\r\n" + 
			"				$.getJSON(\"jsonld/";
	
	public static final String HTML_template_suffix = "\", function(data) {\r\n" + 
			"			    	document.getElementById(\"contents\").innerHTML += template(data);\r\n" + 
			"				});\r\n" + 
			"		\r\n" + 
			"				</script>\r\n" + 
			"			</div>\r\n" + 
			"		</div>\r\n" + 
			"	</body>\r\n" + 
			"</html>";
	
	/**
	 * Convert turtle file content to JSON-LD string
	 * @param filename: specify turtle filename to convert to JSON-LD
	 * @return JSON-LD string representing the conversion
	 */
	static String printJSONLD(String filename){
		try {
			return RDFToJSON.getPrettyJsonLdString(new FileInputStream(filename), RDFFormat.TURTLE);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * Check if a given object literal string has a language marker in it
	 * e.g. '@en'
	 * @param str: string to search on
	 * @return true if the given string contains language marker, false otherwise
	 */
	static boolean hasLanguageMarker(String str){		
		return str.contains("@");
	}
	
	/**
	 * Generator for Drugbank ids
	 * @param i: just a numeric index for a Drugbank id. 
	 * e.g. if i = 1 then we should generate DB00001 as the drugbank id
	 * @return Drugbank id representing the ith drug in Drugbank
	 */
	static String getDrugbankID(int i){
		String result = "DB";
		if (i > 0 && i < 1000){
			int noOfDigits = String.valueOf(i).length();
			int zeros = 5 - noOfDigits;
			for (int j = 0;j < zeros;j++)
				result+="0";
			result+=i;
			return result;
		}
		else{
			return "DB00001"; // return first id
		}
	}
	
	/**
	 * 
	 * @param rc: SPARQL endpoint connection object
	 * @param type: the property of http://schema.org/Drug for whose value (object) we are searching
	 * @param obj: the current identifier which we have, instead of the value we are searching for
	 * @return the object literal which represents the value for the given property
	 */
	static Value getLeafLiteralForIdentifier(RepositoryConnection rc, String type, Value obj){
		Value result = null;
		String property = "purl:title";
		if (type.equals("http://schema.org/mechanismOfAction") || type.equals("http://schema.org/manufacturer")){
			if (type.equals("http://schema.org/mechanismOfAction")){
				property = "purl:description";
			}
			else{
				property = "rdf:value";
			}
		}
		
		String queryString = 
				" PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " + 
				" PREFIX purl: <http://purl.org/dc/terms/> " + 									
				" SELECT ?o WHERE " +  
				" { " +
				" <"+obj.stringValue()+"> " + property + " ?o. " +
				" } limit 1";

		TupleQuery tupleQuery = rc.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
		TupleQueryResult queryResult = tupleQuery.evaluate();		
		result = queryResult.next().getValue("o");
		
		if (hasLanguageMarker(result.toString())){
			// Has language marker
			Value newObj = SimpleValueFactory.getInstance().createLiteral(result.stringValue());
			result = newObj;
		}

		return result;
	}
	
	/**
	 * Write JSON-LD content to file
	 * @param content: content to write to JSON-LD file
	 * @param filename: file path to write JSON-LD content to
	 */
	static void writeToJSONFile(String content, String filename){
		BufferedWriter bw = null;
		FileWriter fw = null;

		try {
			fw = new FileWriter(filename);
			bw = new BufferedWriter(fw);
			bw.write(content);
			System.out.println(filename + " done.");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bw != null)
					bw.close();

				if (fw != null)
					fw.close();

			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	static void embedInHTMLFile(String jsonContent, String filename, String drugbankid){
		String content = HTML_template_prefix + jsonContent + HTML_template_midfix + drugbankid + ".json" + HTML_template_suffix;
		BufferedWriter bw = null;
		FileWriter fw = null;

		try {
			fw = new FileWriter(filename);
			bw = new BufferedWriter(fw);
			bw.write(content);
			System.out.println(filename + " done.");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bw != null)
					bw.close();

				if (fw != null)
					fw.close();

			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		//printJSONLD();		
		// File I/O
		FileOutputStream fop = null;														 		
		// Initialise connection to SPARQL endpoint
		Repository repository = new SPARQLRepository("http://drugbank.bio2rdf.org/sparql");	
		repository.initialize();
		RepositoryConnection connection = null;		
		// Execute SPARQL construct for each drug in Drugbank
		try {			
			connection = repository.getConnection();
			// Loop through drugs
			for (int i = 95;i < 96;i++){
				// Get Drugbank id for this iteration
				String drugid = getDrugbankID(i);
				// Specify SPARQL construct query
				String queryString = 
						" PREFIX purl: <http://purl.org/dc/terms/> " + 
								" PREFIX schema: <http://schema.org/> " + 					
								" PREFIX db: <http://bio2rdf.org/drugbank_vocabulary:> " +  
								" CONSTRUCT { ?s schema:name ?o13. ?s schema:activeIngredient ?o1. ?s schema:administrationRoute ?o2. ?s schema:availableStrength ?o3. ?s schema:cost ?o4. ?s schema:dosageForm ?o5. ?s schema:doseSchedule ?o6. ?s schema:drugUnit ?o7. ?s schema:foodWarning ?o8. ?s schema:interactingDrug ?o9. ?s schema:legalStatus ?o10. ?s schema:manufacturer ?o11. ?s schema:mechanismOfAction ?o12. ?s schema:alternateName ?o14. ?s schema:description ?o15. ?s schema:identifier ?o16. <http://bio2rdf.org/drugbank:"+drugid+"> schema:sameAs \"https://www.drugbank.ca/drugs/"+drugid+"\". <http://bio2rdf.org/drugbank:"+drugid+"> schema:url \"https://www.drugbank.ca/drugs/"+drugid+"\". ?s a schema:Drug. " +
								" } " +
								" { " +
								" ?s a db:Resource. OPTIONAL {?s purl:title ?o13}. OPTIONAL {?s db:name ?o13}. OPTIONAL {?s db:ingredient ?o1}.  OPTIONAL {?s db:route ?o2}. OPTIONAL {?s db:strength ?o3}. OPTIONAL {?s db:price ?o4}. OPTIONAL {?s db:form ?o5}. OPTIONAL {?s db:dosage ?o6}. OPTIONAL {?s db:Unit ?o7}. OPTIONAL {?s db:Food-interaction ?o8}. OPTIONAL {?s db:Drug-Drug-Interaction ?o9}. OPTIONAL {?s db:group ?o10}. OPTIONAL {?s db:manufacturer ?o11}. OPTIONAL {?s db:mechanism-of-action ?o12}. OPTIONAL {?s db:synonym ?o14}. OPTIONAL {?s purl:description ?o15}. OPTIONAL {?s db:identifier ?o16}. OPTIONAL {?s db:url ?o17}. " + 
								" filter (?s = <http://bio2rdf.org/drugbank:"+drugid+">). " +
								" } limit 1";
				// Initialise SPARQL RDF graph query
				GraphQuery query = connection.prepareGraphQuery(queryString);
				// Process the resulting triples
				try (GraphQueryResult result = query.evaluate()) {
					// Final list of triples to convert to JSON
					ArrayList<Statement> statements = new ArrayList<Statement>();
					// Iterate over all triples in the result...
					while (result.hasNext()) {
						Statement st = result.next();					
						// Check for and remove @en language marker from the literal in this triple
						if (hasLanguageMarker(st.getObject().toString())){
							// Has language marker so we create a new triple removing the language marker
							Value obj = SimpleValueFactory.getInstance().createLiteral(st.getObject().stringValue());
							st = SimpleValueFactory.getInstance().createStatement(st.getSubject(), st.getPredicate(), obj);						
						}
						/* Modify triples we get back because some of them have objects 
						 * which point to other properties which hold their ultimate values 
						 */
						// Get the object of the current triple
						Value obj = st.getObject();
						// If it is a literal then we don't need to modify it so we add it to the final list of triples
						if (obj instanceof Literal || (obj.stringValue().contains("http://schema.org"))) {
							statements.add(st);
						}
						else{ 
							/* The object is not a literal so we need to resolve this identifier to a concrete literal value
							 * Need to get property e.g. rdf:label, rdf:value, purl:title or purl:description for identifier
							 * For different properties of a drug the predicate which points to the concrete value differs:
							 * MechanismOfAction 	-> purl:description
							 * doseSchedule 		-> purl:title
							 * manufacturer 		-> rdf:value
							 * legalStatus 			-> purl:title
							 * alternateName 		-> purl:title
							 */
							// Get the concrete literal
							Value leafLiteralObj = getLeafLiteralForIdentifier(connection, st.getPredicate().stringValue(), obj);					
							// Generate new triple with this concrete literal as the object 
							Statement newStatement = SimpleValueFactory.getInstance().createStatement(st.getSubject(), st.getPredicate(), leafLiteralObj);	
							// Add to the final list (instead of the original triple)
							statements.add(newStatement);
						}
					}

					/*
					 * Write triples to file in turtle syntax
					 */
					String jsonldString = "";
					try {						
						File outputFile = new File("html/turtle/"+drugid+".ttl");
						fop = new FileOutputStream(outputFile);
						Rio.write(statements, fop, RDFFormat.TURTLE);
					}
					catch (IOException e) {
						e.printStackTrace(System.err);
					} 
					finally {	
						// Close file output stream
						try {
							if (fop != null) {
								fop.close();
							}
						} catch (IOException e) {
							e.printStackTrace(System.err);
						}
						// Convert the turtle syntax to JSON-LD and also write this to file
						try{
							jsonldString = printJSONLD("html/turtle/"+drugid+".ttl");
							writeToJSONFile(jsonldString, "html/jsonld/"+drugid+".json");
						}
						catch(Exception e){
							e.printStackTrace(System.err);
						}
						finally {
							// Embed JSON-LD in HTML file
							try{
								embedInHTMLFile(jsonldString, "html/"+drugid+".html", drugid);
							}
							catch(Exception e){
								e.printStackTrace(System.err);
							}
						}						
					}
				}
				catch(Exception e){
					e.printStackTrace(System.err);
				}
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		finally{
			// Close the SPARQL endpoint connection
			repository.shutDown();
		}
		// Termination check
		System.out.println("done");
	}
	

}
