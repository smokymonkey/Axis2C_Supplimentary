import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class XSDCleaner {
	private Document xsddoc;
	private boolean preProcessed;
	public Document getDoc(){
		return xsddoc;
	}
	public XSDCleaner(String filename){
		try{
			/*File xsdfile = new File(filename);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setNamespaceAware(true);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			xsddoc = dBuilder.parse(xsdfile);*/
			FileInputStream fis = new FileInputStream(new File(filename));
			xsddoc = PositionalXMLReader.readXML(fis);
			fis.close();
			preProcessed = false;

		}catch(Exception e){
			e.printStackTrace();
		}
	}
	public void cleanXSD(){

		/*NodeList Seqelements = xsddoc.getElementsByTagName("xsd:sequence");
		System.out.println(String.valueOf(Seqelements.getLength())+" sequence elements");*/
		removeEmptyElements();
		removeDanglingComplexType();
		unfoldSeqElements();
		cleanTypes();

	}

	public void cleanTypes(){
		Map<String, Element> userTypeDefs = allDefinedUserTypes();
		Set<String> referedUserTypeNames = allReferedUserTypeNames(userTypeDefs);
		Set<String> definedUserTypeNames = userTypeDefs.keySet();
		for(String name : definedUserTypeNames){
			if (!referedUserTypeNames.contains(name)){
				Node n =userTypeDefs.get(name);
				n.getParentNode().removeChild(n);
			}
		}
	}
	
	private Set<String> allReferedUserTypeNames(Map<String,Element> allDefinedUserTypes){
		
		Set<String> typeNames = new HashSet<String>();
		Element root = xsddoc.getDocumentElement();
		NodeList elementNodes = root.getElementsByTagName("xsd:element");
		//System.out.println(elementNodes.getLength());
		for (int i =0;i<elementNodes.getLength();i++){
			Element e = (Element) elementNodes.item(i);
			if (e.getParentNode()!= root)
				continue;
			//System.out.println("Processing element node at " + String.valueOf(e.getUserData("lineNumber"))+ " "+ e.getAttribute("name"));
			allReferedUserTypeNames(e,allDefinedUserTypes,typeNames);
		}
		return typeNames;
	}
	
	private void allReferedUserTypeNames(Element e,Map<String,Element> allDefinedUserTypes,Set<String> visited){
		String localTypeName;
		//System.out.println("Processing element node at " + String.valueOf(e.getUserData("lineNumber"))+ " "+ e.getAttribute("name"));
		Set<String> extensions = getExtensionType(e);
		for(String type : extensions){
			if (!visited.contains(type)){
				visited.add(type);
				Element typeDefElement = allDefinedUserTypes.get(type);
				allReferedUserTypeNames(typeDefElement,allDefinedUserTypes,visited);
			}
		}
		if(e.hasAttribute("type") && e.getAttribute("type").startsWith("pfx:")){
			localTypeName = e.getAttribute("type").substring(4);
			//System.out.println("Element at "+String.valueOf(e.getUserData("lineNumber"))+ " has type " + localTypeName+"[visited "+String.valueOf(visited.contains(localTypeName)) +"]");
			if (!visited.contains(localTypeName)){
				visited.add(localTypeName);
				Element typeDefElement = allDefinedUserTypes.get(localTypeName);
				allReferedUserTypeNames(typeDefElement,allDefinedUserTypes,visited);
				
			}
		}
		NodeList children = e.getElementsByTagName("xsd:element");
		for (int i=0;i<children.getLength();i++)
			allReferedUserTypeNames((Element)children.item(i),allDefinedUserTypes,visited);
	}
	
	private Set<String> getExtensionType(Element e){
		NodeList l = e.getElementsByTagName("xsd:extension");
		Set<String> rt = new HashSet<String>();
		for(int i=0;i<l.getLength();i++){
			Element n;
			if(l.item(i).getNodeType()==Node.ELEMENT_NODE){
				n = (Element)l.item(i);

				if(n.hasAttribute("base") && n.getAttribute("base").startsWith("pfx")){
					String localTypeName = n.getAttribute("base");
					localTypeName =localTypeName.substring(4);
					rt.add(localTypeName);
				}
			}
			
		}
		return rt;
	}
	private Map<String,Element> allDefinedUserTypes(){
		Map<String,Element> typeSet = new HashMap<String, Element>();
		List<Node> typedefs = new ArrayList<Node>();
		NodeList typeNodes = xsddoc.getElementsByTagName("xsd:simpleType");
		for (int i = 0;i<typeNodes.getLength();i++)
			typedefs.add(typeNodes.item(i));
		typeNodes = xsddoc.getElementsByTagName("xsd:complexType");
		for (int i = 0;i<typeNodes.getLength();i++)
			typedefs.add(typeNodes.item(i));

		for (Node n : typedefs){
			Element e =(Element)n;
			if (e.hasAttribute("name")){
				if (typeSet.containsKey(e.getAttribute("name")))
					throw new RuntimeException("Duplicated type defintion");
				else
					typeSet.put(e.getAttribute("name"), e);
			}
		}
		return typeSet;
	}
	
	private void trimSeqElements(NodeList seqs){
		for (int i=seqs.getLength()-1;i>=0;i--){
			Node seqNode = seqs.item(i);
			if(trimableSeqNode((Element)seqNode)){
				NodeList children = seqNode.getChildNodes();
				for (int j=0;j<children.getLength();j++)
					seqNode.getParentNode().appendChild(children.item(j));
				seqNode.getParentNode().removeChild(seqNode);

			}
		}
		
	}

	private boolean trimableNode(Node _node){
		if (_node.getNodeType()==Node.TEXT_NODE){
			String s = _node.getNodeValue();
			s=s.trim();
			if (s.isEmpty() || s.charAt(0)=='\n' || s.charAt(0)=='\r' ) return true;
		}
		if (_node.getNodeType() == Node.ELEMENT_NODE){
			if (_node.getNodeName().endsWith("sequence"))
				return trimableSeqNode((Element)(_node));
			else{
				NodeList l = _node.getChildNodes();
				for (int i=0;i<l.getLength();i++){
					if (!trimableNode(l.item(i)))
						return false;
				}
				return true;
			}
		}
		return false;
	}
	
	private boolean trimableSeqNode(Element _seqElement){
		boolean trimable = false;
		int child_element_count = 0;
		//System.out.print ("sequence element at " + String.valueOf(_seqElement.getUserData("lineNumber")));
		NodeList children = _seqElement.getChildNodes(); /*get children nodes of current "Sequence" element*/
		
		boolean onlySeqChild = true;
		for (int i =children.getLength()-1;i>=0;i--){
			Node n = children.item(i);
			if(n.getNodeType()==Node.TEXT_NODE ){ //skip white text node
				continue;
			}
			if(n.getNodeType()==Node.ELEMENT_NODE){
				child_element_count ++;
				Element e = (Element)n;
				if (!e.getTagName().endsWith("sequence"))
					onlySeqChild = false;
			}
		}
		//System.out.print (" has " + String.valueOf(child_element_count)+" children");
		trimable = child_element_count<2 && onlySeqChild==true ;
		/*if (trimable)
			System.out.println(" trimable ");
		else
			System.out.println(" untrimable "); */

		return trimable;
	}
	
	public void combineSeqElement(){
		Node root = xsddoc.getDocumentElement();
		combineSeqElement(root);
	}
	private void combineSeqElement(Node _node){
		if (isSeqElement(_node))
			do{
				if(!combineSeqElements((Element)_node)) break;
			}while(true);
		NodeList l = _node.getChildNodes();
		for(int i =0;i<l.getLength();i++)
			combineSeqElement(l.item(i));
	}
	
	private boolean combineSeqElements(Element _seqElement){
		Node nextNode = _seqElement.getNextSibling();
		if (nextNode==null) return false;
		if (nextNode.getNodeType()!=Node.ELEMENT_NODE){
			throw new RuntimeException("Unexpected node type of "+ nextNode.getNodeType());
		}
		if (isSeqElement(nextNode)){
			NodeList l = nextNode.getChildNodes();
			Element dummyNode = xsddoc.createElement("dummy");
			Node insertbeforeNode = _seqElement.appendChild(dummyNode);
			for (int i=l.getLength()-1;i>=0;i--){
				insertbeforeNode = _seqElement.insertBefore(l.item(i), insertbeforeNode);
			}
			_seqElement.removeChild(_seqElement.getLastChild());
			_seqElement.getParentNode().removeChild(nextNode);
			return true;
		}else
			return false;
	}
	
	private boolean isSeqElement(Node _node){
		return (_node.getNodeType()==Node.ELEMENT_NODE && _node.getNodeName().endsWith("sequence") );
	}
	private boolean isDanglingComplexType(Node _node){
		return (_node.getNodeType()==Node.ELEMENT_NODE && _node.getNodeName().endsWith("complexType") &&
				!((Element)_node).hasAttribute("name")&&
				!_node.getParentNode().getNodeName().endsWith("element")  
				);
	}
	private void preProcess(){
		removePureWhiteNodes(xsddoc.getDocumentElement());
		preProcessed = true;
		
	}
	public void removeDanglingComplexType(){
		Node root = xsddoc.getDocumentElement();
		removeDanglingComplexType(root);
	}
	private void removeDanglingComplexType(Node _node){
		
		NodeList l = _node.getChildNodes();
		for(int i =l.getLength()-1;i>=0;i--)
			removeDanglingComplexType(l.item(i));
		if(isDanglingComplexType(_node)){
			//System.out.println("remove complex type node at " + String.valueOf(_node.getUserData("lineNumber"))+ " "+  _node.getNodeName() );
			Node insertbeforeNode = _node;
			Node parentNode = _node.getParentNode();
			for(int i=l.getLength()-1;i>=0;i--){
				insertbeforeNode = parentNode.insertBefore( l.item(i),insertbeforeNode);
			}
			parentNode.removeChild(_node);
		}
	}
	
	public void removePureWhiteNodes(Node _node){
		Node child = _node.getFirstChild();
		while(child!= null){
			Node tmpNode = child.getNextSibling();
			if (child.getNodeType() == Node.ELEMENT_NODE){
				removePureWhiteNodes(child);
			}
			if(child.getNodeType() == Node.TEXT_NODE){
				String s = child.getNodeValue();
				s =s.trim();
				if (s.isEmpty() || s.charAt(0)=='\r' ||s.charAt(0)=='\n')
					child.getParentNode().removeChild(child);
			}
			child = tmpNode;
		}
	}
	public void unfoldSeqElements(){
		Node root = xsddoc.getDocumentElement();
		unfoldSeqElements(root);
	}
	
	private void unfoldSeqElements(Node _node){
		
		
		NodeList l = _node.getChildNodes();
		for(int i =l.getLength()-1;i>=0;i--)
			unfoldSeqElements(l.item(i));
		if(isSeqElement(_node) && isSeqElement(_node.getParentNode())){
			//System.out.println("unfolding node at " + String.valueOf(_node.getUserData("lineNumber"))+ " "+  _node.getNodeName() );
			Node insertbeforeNode = _node;
			Node parentNode = _node.getParentNode();
			for(int i=l.getLength()-1;i>=0;i--){
				insertbeforeNode = parentNode.insertBefore( l.item(i),insertbeforeNode);
			}
			parentNode.removeChild(_node);
		}
	}
	
	
	public void removeEmptyElements(){
		Node root = xsddoc.getDocumentElement();
		removeEmptyElements((Element)root);
	}
	private void removeEmptyElements(Element _element){
		NodeList l = _element.getChildNodes();
		for(int i = l.getLength()-1;i>=0;i--){
			Node child = l.item(i);
			if (child.getNodeType()==Node.ELEMENT_NODE  )
				removeEmptyElements((Element)child);
		}
		if (_element!=xsddoc.getDocumentElement() && isEmptyElement(_element))
			_element.getParentNode().removeChild(_element);
	}
	
	public boolean isEmptyElement(Element _element){
		return (_element.getChildNodes().getLength()==0 && !_element.hasAttributes());
	}
	
	public static void main(String[] args){
		boolean test = false;
		String outputFileName;
		String serviceName =args[1];
		String wsdldir = args[0]+"/";
		String xsddir = wsdldir+serviceName+"/";
		if (test)
			outputFileName = "test.xsd";
		else
			outputFileName = wsdldir+serviceName+"/"+serviceName+".xsd";
		String inputFileName = wsdldir+serviceName+"/"+serviceName+".xsd";
		XSDCleaner xsdCleaner = new XSDCleaner(inputFileName);
		xsdCleaner.preProcess();
		
		xsdCleaner.cleanXSD();

		DOMSource serviceXSDsource = new DOMSource(xsdCleaner.getDoc());

		StreamResult serviceXSDresult = new StreamResult(new File(outputFileName));

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setAttribute("indent-number", 2);
		Transformer transformer;
		try {
			transformer = transformerFactory.newTransformer();

			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //do we need this if we set attrbute for the transformerFactory

			transformer.transform(serviceXSDsource,serviceXSDresult);
			System.out.println("cleaning XSD is done");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

class tuple<K,V>{
	private K k;
	private V v;
	public tuple(K _k, V _v){
		k = _k;
		v = _v;
	}
	public K getFirst(){
		return k;
	}
	public V getSecond(){
		return v;
	}
	public void setFirst(K _k){
		k = _k;
	}
	public void setSecond(V _v){
		v = _v;
	}
}