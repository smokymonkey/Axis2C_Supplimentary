import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
public class NamespacePrefixTest {
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, TransformerException{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		DocumentBuilder db= dbf.newDocumentBuilder();
		Document doc = db.parse("test.xml");
		NodeList nodes= doc.getElementsByTagNameNS("http://www.w3.org/TR/html4/", "table");
		Element t  =(Element)nodes.item(0);
		System.out.println(t.getNamespaceURI());
		t.setPrefix("h1");
		nodes= doc.getElementsByTagNameNS("http://www.w3.org/TR/html4/", "table");
		
		
		
		DOMSource Implementationsource = new DOMSource(doc);
		StreamResult Implementationresult = new StreamResult(new File("test1.xml"));
		
		
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
	    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
	    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		
	    transformer.transform(Implementationsource, Implementationresult);
	}
}
