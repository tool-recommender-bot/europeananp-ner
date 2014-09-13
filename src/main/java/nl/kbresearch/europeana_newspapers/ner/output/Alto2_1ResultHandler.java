package nl.kbresearch.europeana_newspapers.ner.output;

import nl.kbresearch.europeana_newspapers.ner.container.ContainerContext;
import nl.kbresearch.europeana_newspapers.ner.TextElementsExtractor;

import java.io.*;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * Output in Alto2_1/3 xml format
 *
 * @author Willem Jan Faber
 */


public class Alto2_1ResultHandler extends ResultHandler {
    private ContainerContext context;
    private String name;
    private PrintWriter outputFile;
    private Document altoDocument;
    private String versionString;
    private List<HashMap> entityList = new ArrayList<HashMap>();

    String continuationId = null;
    String continuationLabel = null;

    String prevWord = "";
    String prevType = "";

    boolean prevIsNamed = false;

    int tagCounter = 0;

    public Alto2_1ResultHandler(final ContainerContext context,
                                final String name,
                                final String versionString) {
        this.context = context;
        this.name = name;
        this.versionString = versionString;
    }

    @Override
    public void startDocument() {
    }

    @Override
    public void startTextBlock() {
    }

    @Override
    public void newLine(boolean hyphenated) {
    }

    @Override
    public void addToken(String wordid,
                         String originalContent,
                         String word,
                         String label,
                         String continuationid) {

        HashMap<String, String> mMap = new HashMap<String, String>();

        // Try to find out if this is a continuation of the previous word.
        if (continuationid != null) {
            this.continuationId = continuationid;
            this.continuationLabel = label;
         }

        if (wordid.equals(this.continuationId)) {
            label = this.continuationLabel;
        }

        if (label != null) {
            // Reformat the label to a more readable form.
            label = this.stanfordLabelToHumanReadable(label);

            // Find the alto node with the corresponding wordid.
            // Needed for addint the TAGREFS attribute to the ALTO_string.
            Element domElement = TextElementsExtractor.findAltoElementByStringID(altoDocument, wordid);

            if ((this.prevIsNamed) && (this.prevType.equals(label))) {
                // This is a continuation of a label, eg. J.A de Vries..
                // prevIsNamed indicates that the previous word was also a NE
                // concatenation string to generate one label.

                if (!word.equalsIgnoreCase(this.prevWord.trim())) {
                    // Don't double label names on a hypened word.
                    word = this.prevWord + word;
                }

                this.entityList.remove(this.entityList.size() - 1);
                this.prevWord = word + " ";
                this.prevType = label;

                // Add the TAGREFS attribute to the corresponding String in the alto.
                if (this.tagCounter > 0) {
                    // Prevent negative tag numbers.
                    domElement.setAttribute("TAGREFS",
                                            "Tag" + String.valueOf(this.tagCounter - 1));
                    mMap.put("id",
                             String.valueOf(this.tagCounter - 1));
                } else {
                    domElement.setAttribute("TAGREFS",
                                            "Tag" + String.valueOf(this.tagCounter));
                    mMap.put("id",
                             String.valueOf(this.tagCounter));
                }

                // Create mapping for the TAGS header part of alto2_1.
                mMap.put("label", label);
                mMap.put("word", word);

                // Add tagref mapping to the list.
                this.entityList.add(mMap);
            } else {
                // Add the TAGREFS attribute to the corresponding String in the alto.
                domElement.setAttribute("TAGREFS",
                                        "Tag" + String.valueOf(this.tagCounter));

                // Create mapping for the TAGS header part of ALTO2_1 
                mMap.put("id", String.valueOf(this.tagCounter));
                mMap.put("label", label);
                mMap.put("word", word);

                // Add tagref mapping to the list.
                this.entityList.add(mMap);
                this.tagCounter += 1;
                this.prevWord = word + " ";
                this.prevType = label;
            }
            this.prevIsNamed = true;
        } else {
            this.prevIsNamed = false;
        }
    }

    @Override
    public void stopTextBlock() {
    }

    @Override
    public void stopDocument() {
        // Create xml:
        // <Tags><NamedEntityTag ID="Tag7" TYPE="Person" LABEL="James M Bigstaff "/></Tags>
        // entityList is populated with known NE's
        Element child = altoDocument.createElement("Tags");

        for (HashMap s: this.entityList) {
            Element childOfTheChild = altoDocument.createElement("NamedEntityTag");
            childOfTheChild.setAttribute("TYPE",
                                         (String) s.get("label"));
            childOfTheChild.setAttribute("LABEL",
                                         (String) s.get("word"));
            childOfTheChild.setAttribute("ID",
                                         "Tag" + (String) s.get("id"));
            child.appendChild(childOfTheChild);
        }

        altoDocument.getDocumentElement().appendChild(child);

        NodeList alto = altoDocument.getElementsByTagName("alto");
        Element alto_root = (Element) alto.item(0);

        alto_root.setAttribute("xmlns:xsi",
                               "http://www.w3.org/2001/XMLSchema-instance");
        alto_root.setAttribute("xmlns",
                               "http://www.loc.gov/standards/alto/ns-v2#");
        alto_root.setAttribute("xsi:schemaLocation",
                               "http://www.loc.gov/standards/alto/ns-v2# https://raw.github.com/altoxml/schema/master/v2/alto-2-1-draft.xsd");

        try {
            // Output file for alto2_1 format.
            outputFile = new PrintWriter(
                            new File(context.getOutputDirectory(),
                                     name + ".alto2_1.xml"),
                                     "UTF-8");

            Element element = altoDocument.getDocumentElement();
            // Get current date, and add it to the comment line.
            Calendar currentDate = Calendar.getInstance();
            SimpleDateFormat formatter= new SimpleDateFormat("yyyy/MMM/dd HH:mm:ss");
            String dateNow = formatter.format(currentDate.getTime());
            versionString += " Date/time NER-extraction: " + dateNow + "\n";

            // Add the version information to the output xml.
            Comment comment = altoDocument.createComment(versionString);
            element.getParentNode().insertBefore(comment, element);

            DOMSource domSource = new DOMSource(altoDocument);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            // Reformat output, because of additional nodes added.
            transformer.setOutputProperty(OutputKeys.INDENT,
                                          "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                                          "2");

            // Set the right output encoding.
            transformer.setOutputProperty("encoding",
                                          "ISO-8859-1");

            // Transform the input document to output.
            transformer.transform(domSource, result);

            // Store results to file.
            outputFile.print(writer.toString());
            outputFile.flush();
            outputFile.close();

       } catch(TransformerException error) {
            error.printStackTrace();
       } catch (IOException error) {
            error.printStackTrace();
       }
    }

    @Override
    public void close() {
    }

    @Override
    public void globalShutdown() {
    }

    @Override
    public void setAltoDocument(Document doc) {
        altoDocument = doc;
    }
}