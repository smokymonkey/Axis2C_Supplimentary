import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.xml.sax.InputSource;

import org.apache.commons.io.FileUtils;
public class WriteXMLFile {
	private String serviceName;
	private int version;
	public WriteXMLFile(String _serviceName, int _version){
		serviceName = _serviceName;
		version = _version;
	}
	
	public static void main(String argv[]) {
		try{
			//String serviceName ="MaintainPick";
			String serviceName =argv[1];
			int version = 11;
			//String wsdldir = serviceName+"-"+version+"/";
			String wsdldir = argv[0]+"/";
			String xsddir = wsdldir+serviceName+"/";
			
			File originalwsdl = new File(wsdldir+serviceName+".wsdl");
			File Implementationwsdl = new File(wsdldir+serviceName+"Implementation.wsdl");
			File Interfacewsdl = new File(wsdldir+serviceName+"Interface.wsdl");
			File serviceXSD = new File(xsddir+serviceName+".xsd");
			
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setNamespaceAware(true);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document originalwsdldoc = dBuilder.parse(originalwsdl);
			Document Implementationwsdldoc = dBuilder.newDocument();
			Document Interfacewsdldoc = dBuilder.newDocument();
			Document serviceXSDdoc = dBuilder.parse(serviceXSD);
			
			/*------populate implementation wsdl and remove elements belong to interface--------*/
			Node node = Implementationwsdldoc.importNode(originalwsdldoc.getDocumentElement(), true);
			Implementationwsdldoc.appendChild(node);
			NodeList types = Implementationwsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "types");
			for (int i =0;i<types.getLength();i++)
				Implementationwsdldoc.getDocumentElement().removeChild(types.item(i));
			NodeList messages = Implementationwsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "message");
			for (int i=messages.getLength()-1;i>=0;i--)
				Implementationwsdldoc.getDocumentElement().removeChild(messages.item(i));
			NodeList porttypes = Implementationwsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "portType");
			for (int i =0;i<porttypes.getLength();i++)
				Implementationwsdldoc.getDocumentElement().removeChild(porttypes.item(i));
			//System.out.println("Implementation wsdl is populated and trimed");
			/*-------------------------------------------------------------*/
			/*---- import interface wsdl in implementation  wsdl ---------*/
			Element r = Implementationwsdldoc.getDocumentElement();
			Element importinterface = Implementationwsdldoc.createElement("wsdl11:import");
			importinterface.setAttribute("location", serviceName+"Interface.wsdl");
			importinterface.setAttribute("namespace", r.getAttribute("targetNamespace"));
			r.insertBefore(importinterface, r.getFirstChild());
			//System.out.println("Interface wsdl is imported in implementation wsdl");
			/*----------------------------------------------------------------*/
			/*----remove fault from implementation wsdl --------------------*/
			NodeList l = Implementationwsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "fault");
			for(int i =l.getLength()-1;i>=0;i--)
				l.item(i).getParentNode().removeChild(l.item(i));
			/*--------------------------------------------------------------*/
			/*----populate interface wsdl and remove elements belong to implementation --------*/
			node = Interfacewsdldoc.importNode(originalwsdldoc.getDocumentElement(), true);
			Interfacewsdldoc.appendChild(node);
			NodeList bindings = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "binding");
			for (int i =0;i<bindings.getLength();i++)
				Interfacewsdldoc.getDocumentElement().removeChild(bindings.item(i));
			NodeList services = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "service");
			for (int i =0;i<services.getLength();i++)
				Interfacewsdldoc.getDocumentElement().removeChild(services.item(i));
			//System.out.println("Interface wsdl is populated and trimed");
			/*-------------------------------------------------------------*/
			/*------------------- add name spaces to interface wsdl--------------*/
			Element root = Interfacewsdldoc.getDocumentElement();
			root.setAttribute("xmlns:wsdlsoap", "http://schemas.xmlsoap.org/wsdl/soap/" );
			//root.setAttributeNS("http://www.w3.org/2000/xmlns/","xmlns:wsdlsoap", "http://schemas.xmlsoap.org/wsdl/soap/" );
			root.setAttribute("xmlns:wsrr", "http://wsrr_standard.walmart.com/2.0/" );
			//root.setAttribute("xmlns:flt", "http://www.xmlns.walmartstores.com/Fault/datatypes/MessageFault/1.0/");
			//System.out.println("Name spaces are added to interface wsdl");
			/*-------------------------------------------------------------*/
			
			/*----------------------add policy----------------------------------*/
			String policy_str = "<wsp:Policy xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"" +
				            " wsu:Id=\"UsernameToken\" xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n" +
				            " <wsp:ExactlyOne> "+
				            " <wsp:All> " +
				            " <sp:SupportingTokens xmlns:sp=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702\">" +
				            " <wsp:Policy> " +
				            " <sp:UsernameToken	sp:IncludeToken=\"http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/AlwaysToRecipient\" />" +
				            " </wsp:Policy> " +
				            " </sp:SupportingTokens> " +
				            " </wsp:All> " + 
				            " </wsp:ExactlyOne> " +
				            " </wsp:Policy> ";
			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(policy_str));
			Document doc = dBuilder.parse(is);
			Node policy_node = doc.getDocumentElement();
			policy_node = Interfacewsdldoc.importNode(policy_node, true);
			r = Interfacewsdldoc.getDocumentElement();
            r.insertBefore(policy_node, r.getFirstChild());
            //System.out.println("Policy node is added to interface wsdl");
            /*-------------------------------------------------------------*/
            
            /*--------------------- add import MessageFault------------------*/
            /* NOT WORKING WITH PWSDL2C */
			/*types = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "types");
			Element importele = Interfacewsdldoc.createElement("xsd:import");
			importele.setAttribute("namespace", "http://www.xmlns.walmartstores.com/Fault/datatypes/MessageFault/1.0/");
			importele.setAttribute("schemaLocation", serviceName+"/MessageFault.xsd");
			types.item(0).appendChild(importele);
			System.out.println("MessageFault.xsd is imported in interface wsdl");*/
            
            types = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "types");
            Element schemeele = Interfacewsdldoc.createElement("xsd:schema");
            schemeele.setAttribute("targetNamespace", "http://www.xmlns.walmartstores.com/Fault/datatypes/MessageFault/1.0/");
           
         
            Element includeele = Interfacewsdldoc.createElement("xsd:include");
            includeele.setAttribute("schemaLocation", serviceName+"/MessageFault.xsd");
            schemeele.appendChild(includeele);
            /* Do not add fault, comment out the follow line*/
         //   types.item(0).appendChild(schemeele);
            /*-------------------------------------------------------------*/
			/*----------------------------- add policy reference to input node-------------- */
            NodeList inputs = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "input");
            
            for (int i =inputs.getLength()-1; i>=0 ;i--){
            	Element t =(Element)inputs.item(i);
            	Element PolicyRef = Interfacewsdldoc.createElement("wsp:PolicyReference");
            	PolicyRef.setAttribute("URI", "#UsernameToken");
            	//System.out.println(t.getAttribute("message"));
            	t.appendChild(PolicyRef);
           	
            }
                        
            //System.out.println("Policy reference added to input nodes in interface wsdl");
            /*-------------------------------------------------------------*/
            /*------------- change fault namespace prefix--------------------*/
            messages = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "message");
			for (int i=messages.getLength()-1;i>=0;i--){
				if (messages.item(i).getNodeType()!=Node.ELEMENT_NODE) continue;
				Element m = (Element)messages.item(i);
				/**
				 * remove -outFault
				 */
				if (m.getAttribute("name").endsWith("-outFault")){
					/*NodeList messageparts = m.getChildNodes();
					for (int j=messageparts.getLength()-1;j>=0;j--){
						if (messageparts.item(j).getNodeType()!=Node.ELEMENT_NODE) continue;
						Element p=(Element)messageparts.item(j);
						if (p.hasAttribute("element") && p.getAttribute("element").compareToIgnoreCase("pfx:messageFault")==0)
							p.setAttribute("element", "flt:MessageFault");
					}*/
					m.getParentNode().removeChild(m);
				}
			}
			//*----remove fault from interface wsdl --------------------*/
			l = Interfacewsdldoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "fault");
			for(int i =l.getLength()-1;i>=0;i--)
				l.item(i).getParentNode().removeChild(l.item(i));
			/*-----------------------------------------------------------------------*/
			//System.out.println("Name space prefix for MessageFault is changed in interface wsdl");
			/*-------------------------------------------------------------*/
			
			/*-------------- change <<serviceName>>.xsd--------------------*/
            NodeList imports = serviceXSDdoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
            for (int i =imports.getLength()-1;i>=0;i--){
            	if (imports.item(i).getNodeType()!=Node.ELEMENT_NODE) continue;
            	Element el = (Element)imports.item(i);
            	if (el.hasAttribute("schemaLocation") && el.getAttribute("schemaLocation").compareTo("MessageFault.xsd")==0 ){
            		el.getParentNode().removeChild(el);
            	}
            }
            //System.out.println("Import of MessageFault in "+serviceName+".xsd is removed");
            NodeList elements = serviceXSDdoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
            
            for ( int i = elements.getLength()-1;i>=0;i--){
            	if (elements.item(i).getNodeType()!=Node.ELEMENT_NODE) continue;
            	Element el = (Element)elements.item(i);
            	if (el.hasAttribute("name") && el.getAttribute("name").compareToIgnoreCase("messageHeader")==0 &&
            		el.hasAttribute("type") && el.getAttribute("type").compareToIgnoreCase("hdr:MessageHeader")==0){
            		el.removeAttribute("name");
            		el.removeAttribute("type");
            		el.setAttribute("ref", "hdr:MessageHeader");
            	}
            	/*--------------------remove MessagFault element-----------------*/
            	if (el.hasAttribute("name") && el.getAttribute("name").compareToIgnoreCase("messageFault")==0 &&
                		el.hasAttribute("type") && el.getAttribute("type").compareToIgnoreCase("flt:MessageFault")==0){
            		el.getParentNode().removeChild(el);
            	}
            	/*----------------------------------------------*/
            }
            //System.out.println("MessageHeader and MessageFault elements are changed");
            /*----------------------------------------------------------*/
            /*out put to implementation and interface wsdl*/
            //FileUtils.copyFile(new File("standardXSD/MessageFault.xsd"), new File(xsddir+"MessageFault.xsd"));
            //FileUtils.copyFile(new File("standardXSD/MessageHeader.xsd"), new File(xsddir+"MessageHeader.xsd"));

			DOMSource Implementationsource = new DOMSource(Implementationwsdldoc);
			DOMSource Interfacesource = new DOMSource(Interfacewsdldoc);
			DOMSource serviceXSDsource = new DOMSource(serviceXSDdoc);
			
			StreamResult Implementationresult = new StreamResult(Implementationwsdl);
			StreamResult Interfaceresult = new StreamResult(Interfacewsdl);
			StreamResult serviceXSDresult = new StreamResult(serviceXSD);
			
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", 2);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); /*do we need this if we set attrbute for the transformerFactory*/
			
		    transformer.transform(Implementationsource, Implementationresult);
			transformer.transform(Interfacesource, Interfaceresult);
			transformer.transform(serviceXSDsource,serviceXSDresult);
			FileUtils.deleteQuietly(originalwsdl);
			//FileUtils.copyFile(new File("standardXSD/MessageFault.xsd"),new File( xsddir+"/MessageFault.xsd"));
			FileUtils.deleteQuietly(new File( xsddir+"/MessageFault.xsd"));
			FileUtils.deleteQuietly(new File( xsddir+"/MessageHeader.xsd"));
			WriteXMLFile tmp = new WriteXMLFile(serviceName,1);
			URL url = tmp.getClass().getResource("MessageHeader.xsd");
			//FileUtils.copyFile(new File(url.getPath()),new File( xsddir+"/MessageHeader.xsd"));
			try {
		         

		         // returns the ClassLoader object associated with this Class
		         ClassLoader cLoader = tmp.getClass().getClassLoader();
		         // input stream
		         InputStream i = cLoader.getResourceAsStream("MessageHeader.xsd");
		         BufferedReader br = new BufferedReader(new InputStreamReader(i));
		         BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(xsddir+"/MessageHeader.xsd")));

		         // reads each line
		         String line;
		         while((line = br.readLine()) != null) {
		        	 bw.write(line+"\n");
		         } 
		         i.close();
		         br.close();
		         bw.close();
		      } 
		      catch(Exception e) {
		         System.out.println(e);
		      }
			
			System.out.println("Spliting wsdl is done");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
