import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTPointer;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.parser.*; 
import org.eclipse.core.runtime.CoreException;

public class GetterGenerator {
	private String folderName;
	private String serviceName;
	private String ifwsdlFileName;
	private Document ifdoc;
	public GetterGenerator(String _folderName, String _serviceName){
		folderName = _folderName;
		serviceName = _serviceName;
		ifwsdlFileName = _serviceName+"Interface.wsdl";
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setNamespaceAware(true);
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			ifdoc = dBuilder.parse(new File(folderName+"/"+ifwsdlFileName));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public List<String> getRequestNames(){
		List<String> requestNames = new ArrayList<String>();
		NodeList inputNodes = ifdoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "input");
		NodeList msgNodes = ifdoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "message");
		String operationName;
		String portName;
		for (int i =0;i<inputNodes.getLength();i++){
			Element inputNode = (Element)inputNodes.item(i);
			try{
				if (inputNode.getParentNode().getNodeName().endsWith("operation") && 
						inputNode.getParentNode().getParentNode().getNodeName().endsWith("portType")){
					String msgName= inputNode.getAttribute("message");
					msgName = msgName.substring(msgName.indexOf(':')+1);
					for (int j =0;j<msgNodes.getLength();j++){
						try{
							Element msgNode = (Element)msgNodes.item(j);
							if (msgNode.getAttribute("name").endsWith(msgName)){
								NodeList l = msgNode.getChildNodes();
								for(int k =0; k<l.getLength();k++){
									if (l.item(k).getNodeType()==Node.ELEMENT_NODE){
										Element tmp = (Element)l.item(k);
										String tmpName = tmp.getAttribute("element");
										requestNames.add(tmpName.substring(tmpName.indexOf(':')+1));
										break;
									}
								}


							}
						}catch(Exception e){
							continue;
						}
					}

				}else
					continue;
			}catch(Exception e){
				continue;
			}
		}
		return requestNames;
	}
	private String adbName2OjectName(String _adbName){
		String objectName = _adbName;
		if (objectName.startsWith("adb_"))
			objectName = objectName.substring(4);
		if (objectName.endsWith("_t"))
			objectName = objectName.substring(0,objectName.length()-2);
		objectName = objectName.replaceFirst("_type[0-9]+", "");
		return objectName;
	}
	public static String adbName2TypeName(String _adbName){
		String typeName = _adbName;
		if (typeName.startsWith("adb_"))
			typeName = typeName.substring(4);
		if (typeName.endsWith("_t"))
			typeName = typeName.substring(0,typeName.length()-2);
		return typeName;
	}

	public static String adbName2SrcFileName(String _adbName){
		String SrcFileName = _adbName;
		if (SrcFileName.endsWith("_t"))
			SrcFileName = SrcFileName.substring(0,SrcFileName.length()-2);
		SrcFileName = SrcFileName+".c";

		return SrcFileName;
	}

	public static String adbName2HeaderFileName(String _adbName){
		String HeaderFileName = _adbName;
		if (HeaderFileName.endsWith("_t"))
			HeaderFileName = HeaderFileName.substring(0,HeaderFileName.length()-2);
		HeaderFileName = HeaderFileName+".h";

		return HeaderFileName;
	}
	public static String typeName2adbName(String _typeName){
		return "adb_"+_typeName+"_t";
	}
	public static boolean isADBType(String _axisName){
		return _axisName.startsWith("adb_") && _axisName.endsWith("_t"); 
	}
	public FuncExtractor getFuncExtractor(String fileName){
		return new FuncExtractor(fileName);
	}
	class FuncExtractor{
		private String fileName;
		private List<FunctionDeclaration> funcs;
		private FuncExtractor(String _fileName){
			funcs = new ArrayList<FunctionDeclaration>();
			fileName =folderName+"/"+_fileName;
			ILanguage l = GCCLanguage.getDefault();
			try{
				Map<String,String> macros = new HashMap<String,String>();
				macros.put("AXIS2_CALL", "");
				IASTTranslationUnit ast = l.getASTTranslationUnit(FileContent.createForExternalFileLocation(fileName), 
						new ScannerInfo(macros ), 
						IncludeFileContentProvider.getEmptyFilesProvider(), 
						null, 0/*ILanguage.OPTION_IS_SOURCE_UNIT|ILanguage.OPTION_SKIP_FUNCTION_BODIES|ILanguage.OPTION_NO_IMAGE_LOCATIONS*/, new DefaultLogService());
				IASTDeclaration[] decs = ast.getDeclarations();
				for (int i =0;i<decs.length;i++){
					if(isFunctionDeclaration(decs[i])){
						FunctionDeclaration fd =parseFunctionDeclaration(decs[i]);
						funcs.add(fd);
					}
				}

			}catch(CoreException e){
				e.printStackTrace();
			}
		}

		public Collection<FunctionDeclaration> getGetterFunctions(){
			List<FunctionDeclaration> getters = new ArrayList<FunctionDeclaration>();
			for(FunctionDeclaration fd : funcs)
				if(fd.getFunctionName().contains("_get_"))
					getters.add(fd);
			return getters;

		}
		public boolean isFunctionDeclaration(IASTDeclaration _astDeclaration){
			IASTNode[] children =_astDeclaration.getChildren();
			for(int j=0;j<children.length;j++){
				if (children[j] instanceof IASTFunctionDeclarator)
					return true;
			}
			return false;
		}
		public FunctionDeclaration parseFunctionDeclaration(IASTDeclaration _astDeclaration){
			if (isFunctionDeclaration(_astDeclaration)){

				IASTNode[] children =_astDeclaration.getChildren();
				FunctionDeclaration fd = new  FunctionDeclaration();
				for(int j=0;j<children.length;j++){
					if ((children[j] instanceof IASTNamedTypeSpecifier ||children[j] instanceof IASTSimpleDeclSpecifier)&& j ==0){
						ParamType rType = new ParamType();
						rType.setType(children[j].getRawSignature());
						if (children[j] instanceof IASTSimpleDeclSpecifier)
							rType.setSimpleType(true);
						else
							rType.setSimpleType(false);
						fd.setReturnType(rType);
						
					}
					if (children[j] instanceof IASTFunctionDeclarator && j ==1){
						parseFunctionSignature(((IASTFunctionDeclarator)children[j]),fd);
					}
				}
				return fd;
			}
			return null;
		}

		public void parseFunctionSignature(IASTFunctionDeclarator _astFuncDecl, FunctionDeclaration _fd){
			IASTNode[] children =  _astFuncDecl.getChildren();
			for ( int i=0;i<children.length;i++){

				if (children[i] instanceof IASTName){
					_fd.setFunctionName(children[i].getRawSignature());
					_fd.getReturnType().setName("rt_"+children[i].getRawSignature());
				}
				if (children[i] instanceof IASTPointer)
					_fd.getReturnType().setPointers();
					//_fd.setReturnPointers();
				if (children[i] instanceof IASTParameterDeclaration){
					parseParameter((IASTParameterDeclaration)(children[i]),_fd);
				}
			}
		}
		public void parseParameter(IASTParameterDeclaration _astParam, FunctionDeclaration _fd){
			IASTNode[] children =  _astParam.getChildren();
			ParamType pt = new ParamType();
			for (int i =0;i<children.length;i++){

				if ((children[i] instanceof IASTNamedTypeSpecifier ||children[i] instanceof IASTSimpleDeclSpecifier)&& i ==0){
					pt.setType(children[0].getRawSignature());
					if (children[i] instanceof IASTSimpleDeclSpecifier)
						pt.setSimpleType(true);
					else
						pt.setSimpleType(false);
				}
				if (children[i] instanceof IASTDeclarator){
					IASTNode[] children1 =  children[i].getChildren();
					for (int j=0;j<children1.length;j++){
						if (children1[j] instanceof IASTName)
							pt.setName(children1[j].getRawSignature());
						if (children1[j] instanceof IASTPointer)
							pt.setPointers();
					}
				}
			}
			_fd.addParam(pt);
		}
	}



	public void createGetterFile(Collection<List<FunctionDeclaration>> callSequences,String requestName,String folderName){
		// Create file
		 BufferedWriter srcout=null;
		 BufferedWriter hdrout=null;
		 String hdrFileName = folderName+"/getter_"+requestName+".h";
		 String srcFileName = folderName+"/getter_"+requestName+".c"; 
        try {
        	FileWriter srcfstream = new FileWriter(srcFileName, true);
            FileWriter hdrfstream = new FileWriter(hdrFileName, true);
            srcout = new BufferedWriter(srcfstream);
            hdrout = new BufferedWriter(hdrfstream);

            hdrout.write("#ifndef __GETTER_"+requestName.toUpperCase()+"_H__\n");
            hdrout.write("#define __GETTER_"+requestName.toUpperCase()+"_H__\n");
            hdrout.write("#include \"adb_"+requestName+".h\"\n\n\n");
			srcout.write("#include \"getter_"+requestName+".h\"\n");
			srcout.write("#define TEST_NULL(exp) if(NULL==(exp)) {l_rc = -1;break;}\n\n");
			
	        for (List<FunctionDeclaration> lfd: callSequences){
				createGettterFunction(lfd,requestName,srcout,hdrout);

			}
	        
	        hdrout.write("\n\n#endif");
	        
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			try {			  //Close the output stream
				srcout.close();
				hdrout.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	       
		}
        
      
	}
	public void createGettterFunction(List<FunctionDeclaration> callSequence,String _requestName,BufferedWriter srcFile,BufferedWriter headerFile){
		List<String> funcCalls = new LinkedList<String>();
		List<String> varDecls = new LinkedList<String>();
		List<ParamType> inputParam = new ArrayList<ParamType>();
		Map<ParamType,String> outputParam = new HashMap<ParamType,String>();
		String funcHeader = "int get_"+_requestName;
		String dummyName ="return";
		int cntSimpleType=0;
		String preReturn ="adb_"+_requestName+"_t";
		String lastObj = _requestName;
		int nCalls = callSequence.size();
		for (int i=0; i<nCalls;i++){
			FunctionDeclaration fd = callSequence.get(i);
			String objectName = getObjectName(preReturn,fd,lastObj);
			if (!objectName.isEmpty()){
				funcHeader= funcHeader+"_"+objectName;
				lastObj = objectName;
				
			}
			preReturn = fd.getReturnType().getType();
			String call = fd.getFunctionName()+"(";
			for(ParamType p:fd.paramIterator()){
				if(outputParam.containsKey(p)){
					call=call+outputParam.get(p)+",";
					outputParam.remove(p);
					continue;
				}
				if (inputParam.contains(p)){
					if(p.isSimpleType()){
						p.setName(p.getName()+String.valueOf(cntSimpleType));
						cntSimpleType++;
						inputParam.add(p);
						call=call+p.getName()+",";
						continue;
					}else{
						call=call+p.getName()+",";
						continue;
					}
				}
				inputParam.add(p);
				call=call+p.getName()+",";
			}
			call = call.substring(0,call.length()-1);
			call=call+");";
			String tmp = fd.getFunctionName();
			tmp = tmp.substring(1,tmp.indexOf("_get_"));
			tmp= tmp.substring(tmp.lastIndexOf("_"));
			
			ParamType output = new ParamType();
			output.setName(dummyName);
			output.setProinter(fd.getReturnType().getPointers());
			output.setType(fd.getReturnType().getType());
			String lv = output.getType();
			
			if (lv.startsWith("adb_"))
				lv=lv.replace("adb", "l");
			if (lv.endsWith("_t"))
				lv= lv.substring(0,lv.length()-2);
			outputParam.put(output, lv);
			
			if (i==nCalls-1) // last call
				lv = "*value ";
			call = lv+"="+call;
			if (i<nCalls-1 && !fd.getReturnType().getPointers().isEmpty())
					call ="TEST_NULL("+call.substring(0,call.length()-1)+")";
			funcCalls.add(call);
			lv =output.getType()+" " + output.getPointers()+" "+lv;
			if (!output.getPointers().isEmpty())
				lv = lv+"=0;";
			varDecls.add(lv);
		}
		varDecls.set(varDecls.size()-1, "int l_rc=0;");
		
		/*String lastCall =funcCalls.get(funcCalls.size()-1);
		lastCall = "*value "+lastCall.substring(lastCall.indexOf("="));
		funcCalls.set(funcCalls.size()-1, lastCall);*/
		String getterFunc="";
		getterFunc += funcHeader;
		getterFunc +="(";
		for (ParamType ip:inputParam)
			getterFunc +=ip.toString()+" , ";
		if(outputParam.size()!=1)
			throw new RuntimeException("Empty output list");
		ParamType finalOutput= (ParamType)outputParam.keySet().toArray()[0];
		
		try {
			headerFile.write(getterFunc+(finalOutput.getType()+finalOutput.getPointers()+"* value);\n"));
			getterFunc += (finalOutput.getType()+finalOutput.getPointers()+"* value){\n");
			for (String vd: varDecls)
				getterFunc += ("\t"+vd+"\n");
			getterFunc +="\n";
			getterFunc +="\tdo{\n";
			for (String fc : funcCalls)
				getterFunc += ("\t\t"+fc+"\n");
			getterFunc +="\t}while(0);\n";
			getterFunc += "\treturn l_rc;\n}";
			srcFile.write(getterFunc);
			srcFile.write("\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		//System.out.println(getterFunc);
		return;
	}
	
	private String getObjectName(String preReturnType,FunctionDeclaration func,String lastObject){
		List<String> skipList = new ArrayList<String>();
		skipList.add("alpha");
		skipList.add(lastObject+"Sequence_");
		List<String> trimPostFix  = new ArrayList<String>();
		trimPostFix.add("_at");
		for(int i =0;i<50;i++)
			trimPostFix.add("_type"+String.valueOf(i));
		if (!preReturnType.endsWith("_t")) return "";
		String prefixS = preReturnType.substring(0,preReturnType.length()-1)+"get_";
		String target = func.getFunctionName().substring(prefixS.length());
		for(String skiped:skipList)
			if (target.startsWith(skiped)) 
				return "";
		for(String postFix:trimPostFix)
			if (target.endsWith(postFix))
				target=target.substring(0,target.lastIndexOf(postFix));
		if (target.compareTo(lastObject)==0)
			return "";
		return target;
	}
	private String getObjectName(String preReturnType,String funcName,String lastObject){
		List<String> skipList = new ArrayList<String>();
		skipList.add("alpha");
		if (!preReturnType.endsWith("_t")) return "";
		String prefixS = preReturnType.substring(0,preReturnType.length()-1)+"get_";
		String target = funcName.substring(prefixS.length());
		if (!target.startsWith(lastObject+"Sequence_")){
			if (target.lastIndexOf("_type")!=-1)
				target =target.substring(0,target.lastIndexOf("_type"));
			/*else
				target = target.substring(0);*/
			for(String skiped:skipList)
				if (target.startsWith(skiped)) 
					return "";
			if (target.compareTo(lastObject)==0)
				return "";
			return target;
		}
		
		return "";
	}
	public static void main(String[] args){
		String serviceName =args[1];
		String wsdldir = args[0];
		GetterGenerator gg = new GetterGenerator(wsdldir,serviceName);
		List<String> requests = gg.getRequestNames();
		
		for(String requestName: requests){
			CallingTrees cts = new CallingTrees();
			String adbName = gg.typeName2adbName(requestName);
			String headerFileName = gg.adbName2HeaderFileName(adbName);
			FuncExtractor fe = gg.getFuncExtractor(headerFileName);
			Collection<FunctionDeclaration> getters = fe.getGetterFunctions();
			for (FunctionDeclaration fd : getters)
				cts.addRootCall(fd);
			cts.growTrees(gg);
			Collection<List<FunctionDeclaration>> callSequences = cts.buildCallSequences();
			gg.createGetterFile(callSequences,requestName, gg.folderName);
			
		}
		System.out.println("Getters are generated");
	}
}


class FunctionDeclaration{
	private ParamType returnType;
//	private String returnPointers="";
	private String functionName;
	private List<ParamType> params;

	public FunctionDeclaration(){
		params = new ArrayList<ParamType>();
	}
	public void addParam(String _pType, String _pName){
		params.add(new ParamType(_pType,_pName));
	}
	public void addParam(ParamType _pt){
		params.add(_pt);
	}
	public void setReturnType(ParamType _rType){
		returnType = _rType;
	}
	public void setFunctionName(String _fName){
		functionName = _fName;
	}
//	public void setReturnPointers(){
//		returnPointers=returnPointers+"*";
//	}
	public Iterable<ParamType> paramIterator(){
		return params;
	}
	public ParamType getReturnType(){
		return returnType;
	}
	public String getFunctionName(){
		return functionName;
	}
	public String toString(){
		String fs = "";
		fs =  returnType.getType() + " " + returnType.getPointers()+functionName + "(";
		for (ParamType pt : params){
			if(fs.endsWith("("))
				fs = fs + pt.toString();
			else
				fs = fs + ", " + pt.toString();
		}
		fs = fs+");";
		return fs;
	}
//	public String getPointers(){
//		return returnPointers;
//	}
}


class ParamType{
	private String pType;
	private String pName;
	private String pPointers="";
	private boolean simpleType;
	public ParamType(){
		pType="";
		pName ="";
		pPointers="";
		simpleType = false;
	}
	public void setSimpleType(boolean _simple){
		simpleType = _simple;
	}
	public boolean isSimpleType(){
		return simpleType;
	}
	public ParamType(String _pType, String _pName){
		pType = _pType;
		pName = _pName;
	}
	public void setProinter(String pointers){
		pPointers = pointers;
	}
	public void setPointers(){
		pPointers = pPointers+"*";
	}
	public String getType(){
		return pType;
	}
	public void setType(String _type){
		pType = _type;
	}
	public String getName(){
		return pName;
	}
	public void setName(String _name){
		pName = _name;
	}
	public String toString(){
		return pType + " " +pPointers+pName;
	}
	public String getPointers(){
		return pPointers;
	}
	@Override
	public boolean equals(Object o){
		if (o instanceof ParamType)
			return ((ParamType)o).getType().compareTo(pType)==0 &&
			((ParamType)o).getPointers().compareTo(pPointers)==0;
		return false;
	}
	@Override
	public int hashCode(){
		return new String(pType+pPointers).hashCode();
	}
}
class CallNode {
	private FunctionDeclaration preCall;
	private List<CallNode> nextCalls;
	public CallNode(FunctionDeclaration _call){
		preCall = _call;
		nextCalls =  new ArrayList<CallNode>();
	}
	public void addNextCall(FunctionDeclaration _nextCall){
		nextCalls.add(new CallNode(_nextCall));
	}
	public ParamType getReturnType(){
		return preCall.getReturnType();
	}
	public Collection<CallNode> getNextCalls(){
		return nextCalls;
	}
	@Override
	public String toString(){
		return preCall.toString();
	}
	public FunctionDeclaration getPreCall(){
		return preCall;
	}
}

class CallingTrees{
	private List<CallNode> roots;
	public CallingTrees(){
		roots = new ArrayList<CallNode>();
	}
	public void addRootCall(FunctionDeclaration _fd){
		roots.add(new CallNode(_fd));
	}
	public CallingTrees(List<FunctionDeclaration> requests){
		roots = new ArrayList<CallNode>();
		for(FunctionDeclaration fd:requests)
			roots.add(new CallNode(fd));
	}
	private void growTree(CallNode root,GetterGenerator gg){
		String returnType = root.getReturnType().getType();
		if (!GetterGenerator.isADBType(returnType)) return;
		String targetFileName = GetterGenerator.adbName2HeaderFileName(returnType);
		GetterGenerator.FuncExtractor fe = gg.getFuncExtractor(targetFileName);
		Iterable<FunctionDeclaration> getters = fe.getGetterFunctions();
		for (FunctionDeclaration fd:getters)
			root.addNextCall(fd);
		for (CallNode cn:root.getNextCalls()){
			System.out.println(cn);
			growTree(cn,gg);
		}
	}
	public void growTrees(GetterGenerator gg){
		for(CallNode cn:roots)
			growTree(cn,gg);
	}
	private Collection<String> tree2String(CallNode root, int level){
		String currentCall = "";
		for (int i=0;i<level;i++)
			currentCall = currentCall+"\t";
		currentCall = currentCall+root.toString()+"\n";
		List<String> rt = new ArrayList<String>();
		Collection<CallNode> nextCalls = root.getNextCalls();
		if (nextCalls.isEmpty()) {
			rt.add(currentCall);
		}else{
			for(CallNode cn:root.getNextCalls())
				for(String nextCallSequence: tree2String(cn,level+1))
					rt.add(currentCall+nextCallSequence);

		}
		return rt;
	}
	public String toString(){
		String rt="";
		for (CallNode cn:roots)
			for(String callSequence: tree2String(cn,0))
				rt=rt+callSequence;
		return rt;
	}
	public Collection<LinkedList<FunctionDeclaration>> buildCallSequences(CallNode root){
		Collection<LinkedList<FunctionDeclaration>> callSequences = new ArrayList<LinkedList<FunctionDeclaration>>();
		FunctionDeclaration currentFD = root.getPreCall();
		Collection<CallNode> nextCalls = root.getNextCalls();
		if (nextCalls.isEmpty()) {
			LinkedList<FunctionDeclaration> ll = new LinkedList<FunctionDeclaration>();
			ll.add(currentFD);
			callSequences.add(ll);
		}else{
			for(CallNode cn:root.getNextCalls()){
				Collection<LinkedList<FunctionDeclaration>> nextCallSequences = buildCallSequences(cn);
				for(LinkedList<FunctionDeclaration> lfd:nextCallSequences)
					lfd.addFirst(currentFD);
				callSequences.addAll(nextCallSequences);
			}
		}
		return callSequences;
	}
	public Collection<List<FunctionDeclaration>> buildCallSequences(){
		Collection<List<FunctionDeclaration>> callSequences = new ArrayList<List<FunctionDeclaration>>();
		for (CallNode cn:roots)
			callSequences.addAll(buildCallSequences(cn));
		return callSequences;
	}
}