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
	
	private static final String tr_open = "<tr>";
	private static final String tr_close = "</tr>";
	
	private static final String ul_open = "<ul>";
	private static final String ul_close = "</ul>";
	
	private static final String li_open = "<li>";
	private static final String li_close = "</li>";

	private static final String td_open_value = "<td>";
	private static final String td_open_key = "<td class=\'dataframeKey\'>";
	private static final String td_close = "</td>";
	
	private static final String link_open1 = "<a href='";
	private static final String link_open2 = "'>";
	private static final String link_close = "</a>";
	
	private static final String HTML_template_prefix = "<!DOCTYPE html>\r\n" + 
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
	
	private static final String HTML_template_midfix1 = "</script>\r\n" + 
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
			"				var templateCode = \"<table><tbody>"; 
	
	private static final String HTML_template_midfix2 = "</tbody></table>\";\r\n" + 
			"				var template = Handlebars.compile(templateCode);\r\n" + 
			"\r\n" + 
			"				$.getJSON(\"jsonld/";
	
	private static final String HTML_template_suffix = "\", function(data) {\r\n" + 
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
	 * Check if a given object literal string has an irrelevant suffix in it
	 * e.g. '^^<...'
	 * @param str: string to search on
	 * @return true if the given string contains the irritating suffix, false otherwise
	 */
	static boolean hasIrritatingSuffix(String str){		
		return str.contains("^^");
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
	
	/*static Statement getDrugFromDDI(Statement oldStatement, Value obj, String rootDrug){
		Statement result;
		String delete_prefix = "http://bio2rdf.org/drugbank_resource:";
		String new_object_uri = "http://www.drugbank.ca/drugs/";
		String str = obj.stringValue();
		String newStr = str.replace(delete_prefix, "");
		newStr = newStr.replace(rootDrug, "");
		newStr = newStr.replace("_", "");
		new_object_uri += newStr;
		Value newObj = SimpleValueFactory.getInstance().createLiteral(new_object_uri);
		
		result = SimpleValueFactory.getInstance().createStatement(oldStatement.getSubject(), oldStatement.getPredicate(), newObj);
		
		return result;
	}*/
	
	static void embedInHTMLFile(String jsonContent, String filename, String drugbankid){		
		/** Generate HTML Data Template
		 *  CODE HERE:
		 */		
		//START
		String dataRows = "";
		//1. Drug name
		dataRows += tr_open + td_open_key + "Drug Name" + td_close;
		dataRows += td_open_value + "{{name}}" + td_close + tr_close;
		//2. Drug description
		dataRows += tr_open + td_open_key + "Description" + td_close;
		dataRows += td_open_value + "{{description}}" + td_close + tr_close;
		//3. Identifier
		dataRows += tr_open + td_open_key + "Identifier" + td_close;
		dataRows += td_open_value + "{{schema:identifier}}" + td_close + tr_close;
		//4. Non-Proprietary name(s)
		dataRows += tr_open + td_open_key + "Non-Proprietary Names" + td_close;
		dataRows += td_open_value + ul_open + "{{#each nonProprietaryName}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{nonProprietaryName}}" + "{{/each}}" + ul_close + td_close + tr_close;
		//5. Proprietary name
		dataRows += tr_open + td_open_key + "Proprietary Name" + td_close;
		dataRows += td_open_value + "{{proprietaryName}}" + td_close + tr_close;
		//6. Drug Class
		dataRows += tr_open + td_open_key + "Drug Classes" + td_close;
		dataRows += td_open_value + ul_open + "{{#each drugClass}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{drugClass}}" + "{{/each}}" + ul_close + td_close + tr_close;
		//7. Clinical Pharmacology
		dataRows += tr_open + td_open_key + "Clinical Pharmacology" + td_close;
		dataRows += td_open_value + "{{clinicalPharmacology}}" + td_close + tr_close;
		//8. Manufacturer
		dataRows += tr_open + td_open_key + "Manufacturer" + td_close;
		dataRows += td_open_value + "{{#with manufacturer}}{{name}}{{/with}}" + td_close + tr_close;
		//9. Mechanism of action
		dataRows += tr_open + td_open_key + "Mechanism of Action" + td_close;
		dataRows += td_open_value + "{{mechanismOfAction}}" + td_close + tr_close;
		//10. Food Warning
		dataRows += tr_open + td_open_key + "Food Warning" + td_close;
		dataRows += td_open_value + "{{foodWarning}}" + td_close + tr_close;
		//11. URL
		dataRows += tr_open + td_open_key + "URL" + td_close;
		dataRows += td_open_value + link_open1 + "{{schema:url}}" + link_open2 + "{{schema:url}}" + link_close + td_close + tr_close;
		//12. Cost
		dataRows += tr_open + td_open_key + "Costs" + td_close;
		dataRows += td_open_value + "{{#if cost.length}}" + ul_open + "{{#each cost}}" + li_open + "{{#with this}}{{drugUnit}} - {{costPerUnit}}{{costCurrency}}{{/with}}" + li_close + "{{/each}}" + ul_close + "{{else}}{{#with cost}}{{drugUnit}} - {{costPerUnit}}{{costCurrency}}{{/with}}{{/if}}" + td_close + tr_close;
		//13. Available Strengths
		dataRows += tr_open + td_open_key + "Available Strength" + td_close;
		dataRows += td_open_value + "{{#if availableStrength.length}}" + ul_open + "{{#each availableStrength}}" + li_open + "{{this.description}}" + li_close + "{{/each}}" + ul_close + "{{else}}{{#with availableStrength}}{{description}}{{/with}}{{/if}}" + td_close + tr_close;
		//14. Dosage Forms
		dataRows += tr_open + td_open_key + "Dosage Forms" + td_close;
		dataRows += td_open_value + ul_open + "{{#each dosageForm}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{dosageForm}}" + "{{/each}}" + ul_close + td_close + tr_close;		
		//15. Administration Routes
		dataRows += tr_open + td_open_key + "Administration Route" + td_close;
		dataRows += td_open_value + ul_open + "{{#each administrationRoute}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{administrationRoute}}" + "{{/each}}" + ul_close + td_close + tr_close;
		//16. Interacting Drugs
		dataRows += tr_open + td_open_key + "Interacting Drugs" + td_close;
		dataRows += td_open_value + ul_open + "{{#each interactingDrug}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{interactingDrug}}" + "{{/each}}" + ul_close + td_close + tr_close;
		//17. Legal Statuses
		dataRows += tr_open + td_open_key + "Legal Statuses" + td_close;
		dataRows += td_open_value + ul_open + "{{#each legalStatus}}" + li_open + "{{this}}" + li_close + "{{else}}" + "{{legalStatus}}" + "{{/each}}" + ul_close + td_close + tr_close;
		//18. SameAs
		dataRows += tr_open + td_open_key + "Same As" + td_close;
		dataRows += td_open_value + ul_open + "{{#each sameAs}}" + li_open + link_open1 + "{{this}}" + link_open2 + "{{this}}" + link_close + li_close + "{{else}}" + link_open1 + "{{sameAs}}" + link_open2 + "{{sameAs}}" + link_close + "{{/each}}" + ul_close + td_close + tr_close;
		//END
		
		/** ----------------------------------------------------------------------------------------------------------------------- **/
		
		/** WRITE TO FILE **/
		
		String content = HTML_template_prefix + jsonContent + HTML_template_midfix1 + dataRows + HTML_template_midfix2 + drugbankid + ".json" + HTML_template_suffix;
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
		// File I/O
		FileOutputStream fop = null;														 		
		// Initialise connection to SPARQL endpoint
		Repository repository = new SPARQLRepository("http://bio2rdf.org/sparql");	
		repository.initialize();
		RepositoryConnection connection = null;		
		// Execute SPARQL construct for each drug in Drugbank
		try {			
			connection = repository.getConnection();
			// Loop through drugs
			for (int i = 201;i < 251;i++){
				// Get Drugbank id for this iteration
				String drugid = getDrugbankID(i);
				// Specify SPARQL construct query
			
				String queryString = "PREFIX purl: <http://purl.org/dc/terms/>\r\n" + 
				"PREFIX schema: <http://schema.org/>\r\n" + 
				"PREFIX db: <http://bio2rdf.org/drugbank_vocabulary:>\r\n" + 
				"PREFIX bio2rdf: <http://bio2rdf.org/bio2rdf_vocabulary:>\r\n" + 
				"\r\n" + 
				"CONSTRUCT {\r\n" + 
				"  ?s a schema:Drug.\r\n" + 
				"  ?s schema:name ?drugName.\r\n" + 
				"  ?s schema:description ?drugDescription.\r\n" + 
				"  ?s schema:identifier ?drugIdentifier.\r\n" + 
				"  ?s schema:url \"https://schemaorg.metadatacenter.org/drugbank/" + drugid + ".html\".\r\n" + 
				"  ?s schema:sameAs ?sameAs.\r\n" + 
				"\r\n" + 
				"  ?s schema:proprietaryName ?proprietaryName.\r\n" + 
				"  ?s schema:nonProprietaryName ?nonProprietaryName.\r\n" + 
				"\r\n" + 
				"  ?s schema:clinicalPharmacology ?clinicalPharmacology.\r\n" + 
				"\r\n" + 
				"  ?s schema:drugClass ?drugClass.\r\n" + 
				"\r\n" + 
				"  ?s schema:cost ?drugCost.\r\n" + 
				"  ?drugCost a schema:DrugCost.\r\n" + 
				"  ?drugCost schema:costPerUnit ?costPerUnit.\r\n" + 								
				"  ?drugCost schema:costCurrency 'USD'.\r\n" + 
				"  ?drugCost schema:drugUnit ?drugUnit.\r\n" + 
				"\r\n" + 
				"  ?s schema:administrationRoute ?administrationRoute.\r\n" + 
				"  ?s schema:dosageForm ?dosageForm.\r\n" + 
				"\r\n" + 
				"  ?s schema:mechanismOfAction ?mechanismOfAction.\r\n" + 
				"\r\n" + 
				"  ?s schema:interactingDrug ?interactingDrug.\r\n" + 
				"  ?s schema:foodWarning ?foodWarning.\r\n" + 
				"\r\n" + 
				"  ?s schema:availableStrength ?strength.\r\n" + 
				"  ?strength a schema:DrugStrength.\r\n" + 
				"  ?strength schema:description ?strengthDescription.\r\n" + 
				"\r\n" + 
				"  ?s schema:legalStatus ?legalStatus.\r\n" + 
				"\r\n" + 
				"  ?s schema:manufacturer ?organization.\r\n" + 
				"  ?organization a schema:Organization.\r\n" + 
				"  ?organization schema:name ?organizationName\r\n" + 
				"}\r\n" + 
				"WHERE {\r\n" + 
				"  ?s a db:Drug;\r\n" + 
				"     dcterms:title ?drugName;\r\n" + 
				"     dcterms:description ?drugDescription;\r\n" + 
				"     dcterms:identifier ?drugIdentifier;\r\n" + 
				"     bio2rdf:uri ?drugUrl;\r\n" + 
				"     rdfs:seeAlso ?sameAs.\r\n" + 
				"  OPTIONAL {\r\n" + 
				"     ?s db:brand [dcterms:title ?proprietaryName].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"     ?s db:synonym [dcterms:title ?nonProprietaryName].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"     ?s db:pharmacodynamics [dcterms:description ?clinicalPharmacology].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"     ?s db:category [dcterms:title ?drugClass].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"     ?s db:product ?drugCost.\r\n" + 
				"     ?drugCost db:price ?costPerUnit.\r\n" + 
				"     ?drugCost dcterms:title ?drugUnit.\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:dosage ?strength.\r\n" + 
				"    ?strength dcterms:title ?strengthDescription.\r\n" + 
				"    ?strength db:route [dcterms:title ?administrationRoute].\r\n" + 
				"    ?strength db:form [dcterms:title ?dosageForm].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:mechanism-of-action [dcterms:description ?mechanismOfAction].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:food-interaction [rdf:value ?foodWarning].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:ddi-interactor-in [dcterms:title ?interactingDrug].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:group [bio2rdf:identifier ?legalStatus].\r\n" + 
				"  }\r\n" + 
				"  OPTIONAL {\r\n" + 
				"    ?s db:manufacturer ?organization.\r\n" + 
				"    ?organization rdf:value ?organizationName.\r\n" + 
				"  }\r\n" + 
				"  FILTER (?s = <http://bio2rdf.org/drugbank:" + drugid + ">).\r\n" + 
				"} ";
				
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
						if (hasLanguageMarker(st.getObject().toString()) || hasIrritatingSuffix(st.getObject().toString())){
							// Has language marker so we create a new triple removing the language marker
							Value obj = SimpleValueFactory.getInstance().createLiteral(st.getObject().stringValue());
							st = SimpleValueFactory.getInstance().createStatement(st.getSubject(), st.getPredicate(), obj);						
						}	
						statements.add(st);
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
					// Error performing SPARQL query
					System.out.println("Error processing drug " + drugid + ".");
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
