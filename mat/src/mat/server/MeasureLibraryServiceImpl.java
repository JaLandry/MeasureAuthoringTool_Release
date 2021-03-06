package mat.server;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import mat.DTO.MeasureNoteDTO;
import mat.DTO.MeasureTypeDTO;
import mat.DTO.OperatorDTO;
import mat.client.clause.clauseworkspace.model.MeasureDetailResult;
import mat.client.clause.clauseworkspace.model.MeasureXmlModel;
import mat.client.clause.clauseworkspace.model.SortedClauseMapResult;
import mat.client.measure.ManageMeasureDetailModel;
import mat.client.measure.ManageMeasureSearchModel;
import mat.client.measure.ManageMeasureSearchModel.Result;
import mat.client.measure.ManageMeasureShareModel;
import mat.client.measure.MeasureNotesModel;
import mat.client.measure.NqfModel;
import mat.client.measure.PeriodModel;
import mat.client.measure.TransferMeasureOwnerShipModel;
import mat.client.measure.service.SaveMeasureResult;
import mat.client.measure.service.ValidateMeasureResult;
import mat.client.shared.MatContext;
import mat.client.shared.MatException;
import mat.dao.AuthorDAO;
import mat.dao.DataTypeDAO;
import mat.dao.MeasureNotesDAO;
import mat.dao.MeasureTypeDAO;
import mat.dao.OrganizationDAO;
import mat.dao.RecentMSRActivityLogDAO;
import mat.dao.clause.MeasureDAO;
import mat.dao.clause.MeasureXMLDAO;
import mat.dao.clause.OperatorDAO;
import mat.dao.clause.QDSAttributesDAO;
import mat.model.Author;
import mat.model.DataType;
import mat.model.LockedUserInfo;
import mat.model.MatValueSet;
import mat.model.MeasureNotes;
import mat.model.MeasureSteward;
import mat.model.MeasureType;
import mat.model.Organization;
import mat.model.QualityDataModelWrapper;
import mat.model.QualityDataSetDTO;
import mat.model.RecentMSRActivityLog;
import mat.model.SecurityRole;
import mat.model.User;
import mat.model.clause.Measure;
import mat.model.clause.MeasureSet;
import mat.model.clause.MeasureShareDTO;
import mat.model.clause.QDSAttributes;
import mat.model.clause.ShareLevel;
import mat.server.service.InvalidValueSetDateException;
import mat.server.service.MeasureLibraryService;
import mat.server.service.MeasureNotesService;
import mat.server.service.MeasurePackageService;
import mat.server.service.UserService;
import mat.server.util.MeasureUtility;
import mat.server.util.ResourceLoader;
import mat.server.util.UuidUtility;
import mat.server.util.XmlProcessor;
import mat.shared.ConstantMessages;
import mat.shared.DateStringValidator;
import mat.shared.DateUtility;
import mat.shared.model.util.MeasureDetailsUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.mapping.MappingException;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

// TODO: Auto-generated Javadoc
/**
 * The Class MeasureLibraryServiceImpl.
 */
public class MeasureLibraryServiceImpl implements MeasureLibraryService {
	
	/** The Constant logger. */
	private static final Log logger = LogFactory.getLog(MeasureLibraryServiceImpl.class);
	
	/** The Constant MEASURE. */
	private static final String MEASURE = "measure";
	
	/** The Constant MEASURE_DETAILS. */
	private static final String MEASURE_DETAILS = "measureDetails";
	
	/**
	 * Constant XPATH Expression for Component measure.
	 */
	private static final String XPATH_EXPRESSION_COMPONENT_MEASURES = "/measure//measureDetails//componentMeasures";
	/**
	 * Constant XPATH Expression for steward.
	 */
	private static final String XPATH_EXPRESSION_STEWARD = "/measure//measureDetails//steward";
	/**
	 * Constant XPATH Expression for Developers.
	 */
	private static final String XPATH_EXPRESSION_DEVELOPERS = "/measure//measureDetails//developers";
	/** The release date. */
	private String releaseDate;
	
	/** The is measure created. */
	private boolean isMeasureCreated;

	
	/**
	 * Comparator.
	 * **/
	private Comparator<QDSAttributes> attributeComparator = new Comparator<QDSAttributes>() {
		@Override
		public int compare(final QDSAttributes arg0, final QDSAttributes arg1) {
			return arg0.getName().toLowerCase().compareTo(arg1.getName().toLowerCase());
		}
	};
	
	/** The context. */
	@Autowired
	private ApplicationContext context;
	
	/** The measure package service. */
	@Autowired
	private MeasurePackageService measurePackageService;
	
	/** The qds attributes dao. */
	@Autowired
	private QDSAttributesDAO qDSAttributesDAO;
	
	/** The recent msr activity log dao. */
	@Autowired
	private RecentMSRActivityLogDAO recentMSRActivityLogDAO;
	
	/** The user service. */
	@Autowired
	private UserService userService;
	
	/** The measure type dao. */
	@Autowired
	private MeasureTypeDAO measureTypeDAO;
	
	/** The author dao. */
	@Autowired
	private AuthorDAO authorDAO;
	
	/** The operator dao. */
	@Autowired
	private OperatorDAO operatorDAO;
	
	/** The organization dao. */
	@Autowired
	private OrganizationDAO organizationDAO;
	/** The x path. */
	javax.xml.xpath.XPath xPath = XPathFactory.newInstance().newXPath();
	
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#appendAndSaveNode(mat.client.clause.clauseworkspace.model.MeasureXmlModel, java.lang.String)
	 */
	@Override
	public final void appendAndSaveNode(final MeasureXmlModel measureXmlModel, final String nodeName) {
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureXmlModel.getMeasureId());
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))
				&& ((nodeName != null) && StringUtils.isNotBlank(nodeName))) {
			String result = callAppendNode(xmlModel, measureXmlModel.getXml(), nodeName, measureXmlModel.getParentNode());
			measureXmlModel.setXml(result);
			getService().saveMeasureXml(measureXmlModel);
		}
		
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#checkAndDeleteSubTree(java.lang.String, java.lang.String)
	 */
	@Override
	public HashMap<String, String> checkAndDeleteSubTree(String measureId , String subTreeUUID){
		logger.info("Inside checkAndDeleteSubTree Method for measure Id " + measureId);
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureId);
		HashMap<String,String> removeUUIDMap= new HashMap<String,String> ();
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try {
				NodeList subTreeRefNodeList = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), "//subTreeRef[@id='"+subTreeUUID+"']");
				if(subTreeRefNodeList.getLength() > 0){
					xmlModel.setXml(null);
					return removeUUIDMap;
				}
				
				Node subTreeNode = xmlProcessor.findNode(xmlProcessor.getOriginalDoc()
						, "/measure/subTreeLookUp/subTree[@uuid='"+subTreeUUID+"']");
				NodeList subTreeOccNode = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc()
						, "/measure/subTreeLookUp/subTree[@instanceOf='"+subTreeUUID+"']");
				if (subTreeNode != null) {
					Node parentNode = subTreeNode.getParentNode();
					String name = subTreeNode.getAttributes().getNamedItem("displayName").getNodeValue();
					String uuid = subTreeNode.getAttributes().getNamedItem("uuid").getNodeValue();
					String mapValue = name + "~" + uuid;
					removeUUIDMap.put(uuid,mapValue);
					parentNode.removeChild(subTreeNode);
				}
				if (subTreeOccNode.getLength() > 0) {
					Set<Node> targetOccurenceElements = new HashSet<Node>();
					for (int i = 0; i < subTreeOccNode.getLength(); i++) {
						Node node = subTreeOccNode.item(i);
						targetOccurenceElements.add(node);
					}
					
					for (Node occNode : targetOccurenceElements) {
						String name = "Occurrence " + occNode.getAttributes().getNamedItem("instance").getNodeValue() + " of ";
						name = name + occNode.getAttributes().getNamedItem("displayName").getNodeValue();
						String uuid = occNode.getAttributes().getNamedItem("uuid").getNodeValue();
						String mapValue = name + "~" + uuid;
						removeUUIDMap.put(uuid,mapValue);
						occNode.getParentNode().removeChild(occNode);
					}
				}
				xmlModel.setXml(xmlProcessor.transform(xmlProcessor.getOriginalDoc()));
				getService().saveMeasureXml(xmlModel);
			} catch (XPathExpressionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return removeUUIDMap;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#isSubTreeReferredInLogic(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean isSubTreeReferredInLogic(String measureId, String subTreeUUID){
		logger.info("Inside isSubTreeReferredInLogic Method for measure Id " + measureId);
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureId);
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try {
				NodeList subTreeRefNodeList = xmlProcessor.findNodeList(
						xmlProcessor.getOriginalDoc(), "//subTreeRef[@id='" + subTreeUUID + "']");
				NodeList subTreeOccNodeList = xmlProcessor.findNodeList(
						xmlProcessor.getOriginalDoc(), "//subTree[@instanceOf='" + subTreeUUID + "']");
				boolean isOccurrenceUsed = false;
				for (int i = 0; i < subTreeOccNodeList.getLength(); i++) {
					Node node = subTreeOccNodeList.item(i);
					if (node.hasAttributes()) {
						String occNodeUUID = node.getAttributes().getNamedItem("uuid").getNodeValue();
						NodeList subTreeOccRefNodeList = xmlProcessor.findNodeList(
								xmlProcessor.getOriginalDoc(), "//subTreeRef[@id='" + occNodeUUID + "']");
						if (subTreeOccRefNodeList.getLength() > 0) {
							isOccurrenceUsed = true;
							break;
						}
					}
				}
				if ((subTreeRefNodeList.getLength() > 0) || isOccurrenceUsed) {
					return true;
				}
			} catch (XPathExpressionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return false;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#isQDMVariableEnabled(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean isQDMVariableEnabled(String measureId, String subTreeUUID){
		logger.info("Inside isQDMVariableEnabled Method for measure Id " + measureId);
		
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureId);
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try {
				NodeList subTreeOccNodeList = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), "//subTree[@instanceOf='"+subTreeUUID+"']");
				if((subTreeOccNodeList.getLength() >0)){
					return true;
				}
			} catch (XPathExpressionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return false;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveSubTreeInMeasureXml(mat.client.clause.clauseworkspace.model.MeasureXmlModel, java.lang.String)
	 */
	@Override
	public SortedClauseMapResult saveSubTreeInMeasureXml(MeasureXmlModel measureXmlModel, String nodeName, String nodeUUID) {
		logger.info("Inside saveSubTreeInMeasureXml Method for measure Id " + measureXmlModel.getMeasureId() + " .");
		SortedClauseMapResult clauseMapResult = new SortedClauseMapResult();
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureXmlModel.getMeasureId());
		
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try {
				Node subTreeLookUpNode = xmlProcessor.findNode(xmlProcessor.getOriginalDoc()
						, measureXmlModel.getParentNode());
				// Add subTreeLookUp node if not available in MeasureXml.
				if (subTreeLookUpNode == null) {
					logger.info("Adding subTreeNodeLookUp Node for measure Id "
							+ measureXmlModel.getMeasureId() + " .");
					String xPathSupplementalDataElement = "/measure/supplementalDataElements";
					Node supplementaDataElementsElement = xmlProcessor.findNode(xmlProcessor.getOriginalDoc(),
							xPathSupplementalDataElement);
					String tagNameSubTeeLookUp = "subTreeLookUp";
					Element subTreeLookUpElement = xmlProcessor.getOriginalDoc()
							.createElement(tagNameSubTeeLookUp);
					((Element) supplementaDataElementsElement.getParentNode())
					.insertBefore(subTreeLookUpElement,
							supplementaDataElementsElement.getNextSibling());
					xmlProcessor.setOriginalXml(xmlProcessor.transform(xmlProcessor.getOriginalDoc()));
				}
				// If Node already exist's and its a update then existing node will be removed from Parent Node
				// and updated node will be added.
				String xPathForSubTree = "/measure/subTreeLookUp/subTree[@uuid='"+nodeUUID+"']";
				NodeList subTreeNodeForUUID = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), xPathForSubTree);
				if(subTreeNodeForUUID.getLength() == 0){
					xmlProcessor.appendNode(measureXmlModel.getXml(), measureXmlModel.getToReplaceNode(), measureXmlModel.getParentNode());
				}else{
					Node newNode = subTreeNodeForUUID.item(0);
					if (newNode.getAttributes().getNamedItem("uuid").getNodeValue().equals(nodeUUID)) {
						logger.info("Replacing SubTreeNode for UUID " + nodeUUID + " .");
						xmlProcessor.removeFromParent(newNode);
						xmlProcessor.appendNode(measureXmlModel.getXml(), measureXmlModel.getToReplaceNode(), measureXmlModel.getParentNode());
						
						//In case the name of the subTree has changed we need to make sure to find all the subTreeRef tags and change the name in them as well.
						NodeList subTreeRefNodeList = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), "//subTreeRef[@id='"+nodeUUID+"']");
						NodeList subTreeOccRefNodeList = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), "//subTreeRef[@instanceOf='"+nodeUUID+"']");
						String xPathForSubTreeOcc = "/measure/subTreeLookUp/subTree[@instanceOf='"+nodeUUID+"']";
						NodeList subTreeOccNodeList = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), xPathForSubTreeOcc);
						if(subTreeRefNodeList.getLength() > 0){
							for(int k=0;k<subTreeRefNodeList.getLength();k++){
								Node subTreeRefNode = subTreeRefNodeList.item(k);
								subTreeRefNode.getAttributes().getNamedItem("displayName").setNodeValue(nodeName);
							}
						}
						if(subTreeOccRefNodeList.getLength() > 0){
							for(int k=0;k<subTreeOccRefNodeList.getLength();k++){
								Node subTreeRefOccNode = subTreeOccRefNodeList.item(k);
								subTreeRefOccNode.getAttributes().getNamedItem("displayName").setNodeValue(nodeName);
							}
						}
						
						if(subTreeOccNodeList.getLength() > 0){
							for(int k=0;k<subTreeOccNodeList.getLength();k++){
								Node subTreeOccNode = subTreeOccNodeList.item(k);
								subTreeOccNode.getAttributes().getNamedItem("displayName").setNodeValue(nodeName);
							}
						}
					}
				}
				xmlProcessor.setOriginalXml(xmlProcessor.transform(xmlProcessor.getOriginalDoc()));
				
				measureXmlModel.setXml(xmlProcessor.getOriginalXml());
				clauseMapResult.setMeasureXmlModel(measureXmlModel);
				getService().saveMeasureXml(measureXmlModel);
			} catch (XPathExpressionException exception) {
				exception.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		logger.info("End saveSubTreeInMeasureXml Method for measure Id " + measureXmlModel.getMeasureId() + " .");
		clauseMapResult.setClauseMap(getSortedClauseMap(measureXmlModel.getMeasureId()));
		return clauseMapResult;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveSubTreeOccurrence(mat.client.clause.clauseworkspace.model.MeasureXmlModel, java.lang.String, java.lang.String)
	 */
	@Override
	public SortedClauseMapResult saveSubTreeOccurrence(MeasureXmlModel measureXmlModel, String nodeName, String nodeUUID){
		logger.info("Inside saveSubTreeOccurrence Method for measure Id " + measureXmlModel.getMeasureId() + " .");
		SortedClauseMapResult sortedClauseMapResult = new SortedClauseMapResult();
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureXmlModel.getMeasureId());
		int ASCII_START = 65;
		int ASCII_END = 90;
		int occurrenceCount = ASCII_START;
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try {
				String xPathForSubTree = "/measure/subTreeLookUp/subTree[@instanceOf='" + nodeUUID + "']";
				NodeList subTreeNodeForUUID = xmlProcessor.findNodeList(xmlProcessor.getOriginalDoc(), xPathForSubTree);
				if (subTreeNodeForUUID.getLength() == 0) {
					XmlProcessor processor = new XmlProcessor(measureXmlModel.getXml());
					Node subTreeNode = processor.findNode(processor.getOriginalDoc(), "/subTree");
					Attr instanceAttrNode = processor.getOriginalDoc().createAttribute("instance");
					instanceAttrNode.setNodeValue("" + (char) occurrenceCount);
					subTreeNode.getAttributes().setNamedItem(instanceAttrNode);
					Attr instanceAttrNodeOfAttrNode = processor.getOriginalDoc().createAttribute("instanceOf");
					instanceAttrNodeOfAttrNode.setNodeValue(nodeUUID);
					subTreeNode.getAttributes().setNamedItem(instanceAttrNodeOfAttrNode);
					measureXmlModel.setXml(processor.transform(subTreeNode));
					xmlProcessor.appendNode(measureXmlModel.getXml(),
							measureXmlModel.getToReplaceNode(), measureXmlModel.getParentNode());
				} else {
					for (int i = 0; i < subTreeNodeForUUID.getLength(); i++) {
						Node node = subTreeNodeForUUID.item(i);
						String instanceValue = node.getAttributes().getNamedItem("instance").getNodeValue();
						Character text = instanceValue.charAt(0);
						int newOcc = (text);
						if (newOcc >= occurrenceCount) {
							occurrenceCount = ++newOcc;
						}
					}
					if (occurrenceCount < ASCII_END) {
						XmlProcessor processor = new XmlProcessor(measureXmlModel.getXml());
						Node subTreeNode = processor.findNode(processor.getOriginalDoc(), "/subTree");
						Attr instanceAttrNode = processor.getOriginalDoc().createAttribute("instance");
						instanceAttrNode.setNodeValue("" + (char) occurrenceCount);
						subTreeNode.getAttributes().setNamedItem(instanceAttrNode);
						Attr instanceAttrNodeOfAttrNode = processor.getOriginalDoc().createAttribute("instanceOf");
						instanceAttrNodeOfAttrNode.setNodeValue(nodeUUID);
						subTreeNode.getAttributes().setNamedItem(instanceAttrNodeOfAttrNode);
						measureXmlModel.setXml(processor.transform(subTreeNode));
						xmlProcessor.appendNode(measureXmlModel.getXml(),
								measureXmlModel.getToReplaceNode(), measureXmlModel.getParentNode());
					}
				}
				xmlProcessor.setOriginalXml(xmlProcessor.transform(xmlProcessor.getOriginalDoc()));
				measureXmlModel.setXml(xmlProcessor.getOriginalXml());
				sortedClauseMapResult.setMeasureXmlModel(measureXmlModel);
				getService().saveMeasureXml(measureXmlModel);
			} catch (XPathExpressionException exception) {
				// TODO Auto-generated catch block
				exception.printStackTrace();
			} catch (SAXException exception) {
				// TODO Auto-generated catch block
				exception.printStackTrace();
			} catch (IOException exception) {
				// TODO Auto-generated catch block
				exception.printStackTrace();
			}
		}
		sortedClauseMapResult.setClauseMap(getSortedClauseMap(measureXmlModel.getMeasureId()));
		return sortedClauseMapResult;
	}
	/**
	 * Method to call XMLProcessor appendNode method to append new xml nodes
	 * into existing xml.
	 * 
	 * @param measureXmlModel
	 *            the measure xml model
	 * @param newXml
	 *            the new xml
	 * @param nodeName
	 *            the node name
	 * @param parentNodeName
	 *            the parent node name
	 * @return the string
	 */
	private String callAppendNode(final MeasureXmlModel measureXmlModel, final String newXml, final String nodeName,
			final String parentNodeName) {
		XmlProcessor xmlProcessor = new XmlProcessor(measureXmlModel.getXml());
		String result = null;
		try {
			result = xmlProcessor.appendNode(newXml, nodeName, parentNodeName);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * This method will use XMLProcessor to determine if which of the 3 Timing
	 * Elements are missing from the measure xml 'elementLookUp' tag. Based on
	 * which one is missing it will fetch it from ListObject and add it to
	 * 'elementLookUp'.
	 * 
	 * @param xmlProcessor
	 *            the xml processor
	 */
	@Override
	public void checkForTimingElementsAndAppend(XmlProcessor xmlProcessor) {
		
		List<String> missingMeasurementPeriod = xmlProcessor.checkForTimingElements();
		
		if (missingMeasurementPeriod.isEmpty()) {
			logger.info("All timing elements present in the measure.");
			return;
		}
		logger.info("Found the following timing elements missing:" + missingMeasurementPeriod);
		
		//		List<String> missingOIDList = new ArrayList<String>();
		//		missingOIDList.add(missingMeasurementPeriod);
		
		QualityDataModelWrapper wrapper = getMeasureXMLDAO().createTimingElementQDMs(missingMeasurementPeriod);
		
		// Object to XML for elementLookUp
		ByteArrayOutputStream streamQDM = XmlProcessor.convertQualityDataDTOToXML(wrapper);
		
		String filteredString = removePatternFromXMLString(streamQDM.toString().substring(streamQDM.toString().indexOf("<measure>", 0)),
				"<measure>", "");
		filteredString = removePatternFromXMLString(filteredString, "</measure>", "");
		
		try {
			xmlProcessor.appendNode(filteredString, "qdm", "/measure/elementLookUp");
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Method called when Measure Details Clone operation is done or Drafting of
	 * a version measure is done. TODO: Sangeethaa This method will have to
	 * change when we get all the page items captued as XML 1) The
	 * MeasureDAO.clone() method should be re written in here
	 * 
	 * @param creatingDraft
	 *            the creating draft
	 * @param oldMeasureId
	 *            the old measure id
	 * @param clonedMeasureId
	 *            the cloned measure id
	 */
	@Override
	public final void cloneMeasureXml(final boolean creatingDraft, final String oldMeasureId, final String clonedMeasureId) {
		logger.info("In MeasureLibraryServiceImpl.cloneMeasureXml() method. Clonig for Measure: " + oldMeasureId);
		ManageMeasureDetailModel measureDetailModel = null;
		if (creatingDraft) {
			measureDetailModel = getMeasure(oldMeasureId);// get the
			// measureDetailsmodel
			// object for which
			// draft have to be
			// created..
			Measure measure = getService().getById(clonedMeasureId);// get the
			// Cloned Measure Revision Number reset to '000' when cloned.
			measure.setRevisionNumber("000");
			// Cloned
			// version
			// of the
			// Measure.
			createMeasureDetailsModelFromMeasure(measureDetailModel, measure); // apply
			// measure
			// values
			// in
			// the
			// created
			// MeasureDetailsModel.
		} else {
			measureDetailModel = getMeasure(clonedMeasureId);
		}
		MeasureXmlModel measureXmlModel = new MeasureXmlModel();
		measureXmlModel.setMeasureId(measureDetailModel.getId());
		measureXmlModel.setXml(createXml(measureDetailModel).toString());
		measureXmlModel.setToReplaceNode(MEASURE_DETAILS);
		saveMeasureXml(measureXmlModel);
		logger.info("Clone of Measure_xml is Successful");
	}
	
	/**
	 * Adding additonal fields in model from measure table.
	 * 
	 * @param manageMeasureDetailModel
	 *            - {@link ManageMeasureDetailModel}.
	 * @param measure
	 *            - {@link Measure}.
	 */
	private void convertAddlXmlElementsToModel(final ManageMeasureDetailModel manageMeasureDetailModel, final Measure measure) {
		logger.info("In easureLibraryServiceImpl.convertAddlXmlElementsToModel()");
		manageMeasureDetailModel.setId(measure.getId());
		manageMeasureDetailModel.setCalenderYear(manageMeasureDetailModel.getPeriodModel().isCalenderYear());
		if(!manageMeasureDetailModel.getPeriodModel().isCalenderYear()){
		manageMeasureDetailModel.setMeasFromPeriod(manageMeasureDetailModel.getPeriodModel() != null ? manageMeasureDetailModel
				.getPeriodModel().getStartDate() : null);
		manageMeasureDetailModel.setMeasToPeriod(manageMeasureDetailModel.getPeriodModel() != null ? manageMeasureDetailModel
				.getPeriodModel().getStopDate() : null);
		}
		manageMeasureDetailModel.setEndorseByNQF((StringUtils.isNotBlank(
				manageMeasureDetailModel.getEndorsement()) ? true : false));
		manageMeasureDetailModel.setOrgVersionNumber(MeasureUtility.formatVersionText(measure.getRevisionNumber(),
				String.valueOf(measure.getVersionNumber())));
		manageMeasureDetailModel.setVersionNumber(MeasureUtility.getVersionText(manageMeasureDetailModel.getOrgVersionNumber(),
				measure.isDraft()));
		manageMeasureDetailModel.setFinalizedDate(DateUtility.convertDateToString(measure.getFinalizedDate()));
		manageMeasureDetailModel.setDraft(measure.isDraft());
		manageMeasureDetailModel.setValueSetDate(DateUtility.convertDateToStringNoTime(measure.getValueSetDate()));
		manageMeasureDetailModel.setNqfId(manageMeasureDetailModel.getNqfModel() != null ?
				manageMeasureDetailModel.getNqfModel()
				.getExtension() : null);
		manageMeasureDetailModel.seteMeasureId(measure.geteMeasureId());
		manageMeasureDetailModel.setMeasureOwnerId(measure.getOwner().getId());
		logger.info("Exiting easureLibraryServiceImpl.convertAddlXmlElementsToModel() method..");
	}
	
	/**
	 * Method un marshalls MeasureXML into ManageMeasureDetailModel object.
	 * 
	 * @param xmlModel
	 *            -MeasureXmlModel
	 * @param measure
	 *            - Measure
	 * @return ManageMeasureDetailModel
	 */
	private ManageMeasureDetailModel convertXmltoModel(final MeasureXmlModel xmlModel, final Measure measure) {
		logger.info("In MeasureLibraryServiceImpl.convertXmltoModel()");
		ManageMeasureDetailModel details = null;
		String xml = null;
		if ((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml())) {
			xml = new XmlProcessor(xmlModel.getXml()).getXmlByTagName(MEASURE_DETAILS);
		}
		try {
			if (xml == null) { // TODO: This Check should be replaced when the
				// DataConversion is complete.
				logger.info("xml is null or xml doesn't contain measureDetails tag");
				details = new ManageMeasureDetailModel();
				createMeasureDetailsModelFromMeasure(details, measure);
			} else {
				Mapping mapping = new Mapping();
				mapping.loadMapping(new ResourceLoader().getResourceAsURL("MeasureDetailsModelMapping.xml"));
				Unmarshaller unmar = new Unmarshaller(mapping);
				unmar.setClass(ManageMeasureDetailModel.class);
				unmar.setWhitespacePreserve(true);
				// logger.info("unmarshalling xml.. " + xml);
				details = (ManageMeasureDetailModel) unmar.unmarshal(new InputSource(new StringReader(xml)));
				// logger.info("unmarshalling complete.." + details.toString());
				convertAddlXmlElementsToModel(details, measure);
			}
			
		} catch (Exception e) {
			if (e instanceof IOException) {
				logger.info("Failed to load MeasureDetailsModelMapping.xml" + e);
			} else if (e instanceof MappingException) {
				logger.info("Mapping Failed" + e);
			} else if (e instanceof MarshalException) {
				logger.info("Unmarshalling Failed" + e);
			} else {
				logger.info("Other Exception" + e);
			}
		}
		return details;
	}
	
	/**
	 * Convert xmlto quality data dto model.
	 * 
	 * @param xmlModel
	 *            the xml model
	 * @return the quality data model wrapper
	 */
	private QualityDataModelWrapper convertXmltoQualityDataDTOModel(final MeasureXmlModel xmlModel) {
		logger.info("In MeasureLibraryServiceImpl.convertXmltoQualityDataDTOModel()");
		QualityDataModelWrapper details = null;
		String xml = null;
		if ((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml())) {
			xml = new XmlProcessor(xmlModel.getXml()).getXmlByTagName("measure");
			// logger.info("xml by tag name elementlookup" + xml);
		}
		try {
			if (xml == null) {// TODO: This Check should be replaced when the
				// DataConversion is complete.
				logger.info("xml is null or xml doesn't contain elementlookup tag");
				
			} else {
				Mapping mapping = new Mapping();
				mapping.loadMapping(new ResourceLoader().getResourceAsURL("QualityDataModelMapping.xml"));
				Unmarshaller unmar = new Unmarshaller(mapping);
				unmar.setClass(QualityDataModelWrapper.class);
				unmar.setWhitespacePreserve(true);
				// logger.info("unmarshalling xml..elementlookup " + xml);
				details = (QualityDataModelWrapper) unmar.unmarshal(new InputSource(new StringReader(xml)));
				logger.info("unmarshalling complete..elementlookup" + details.getQualityDataDTO().get(0).getCodeListName());
			}
			
		} catch (Exception e) {
			if (e instanceof IOException) {
				logger.info("Failed to load QualityDataModelMapping.xml" + e);
			} else if (e instanceof MappingException) {
				logger.info("Mapping Failed" + e);
			} else if (e instanceof MarshalException) {
				logger.info("Unmarshalling Failed" + e);
			} else {
				logger.info("Other Exception" + e);
			}
		}
		return details;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#createAndSaveElementLookUp(java.util.ArrayList, java.lang.String)
	 */
	@Override
	public final void createAndSaveElementLookUp(final List<QualityDataSetDTO> list, final String measureID) {
		QualityDataModelWrapper wrapper = new QualityDataModelWrapper();
		wrapper.setQualityDataDTO(list);
		ByteArrayOutputStream stream = createQDMXML(wrapper);
		int startIndex = stream.toString().indexOf("<elementLookUp>", 0);
		int lastIndex = stream.toString().indexOf("</measure>", startIndex);
		String xmlString = stream.toString().substring(startIndex, lastIndex);
		String nodeName = "elementLookUp";
		
		MeasureXmlModel exportModal = new MeasureXmlModel();
		exportModal.setMeasureId(measureID);
		exportModal.setParentNode("/measure");
		exportModal.setToReplaceNode("elementLookUp");
		System.out.println("XML " + xmlString);
		
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureID);
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml())) && ((nodeName != null) && StringUtils.isNotBlank(nodeName))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			String result = xmlProcessor.replaceNode(xmlString, nodeName, "measure");
			System.out.println("result" + result);
			exportModal.setXml(result);
			getService().saveMeasureXml(exportModal);
		}
		
	}
	
	/**
	 * This should be removed when we do a batch save in Measure_XML on
	 * production.
	 * 
	 * @param model
	 *            the model
	 * @param measure
	 *            the measure
	 */
	private void createMeasureDetailsModelFromMeasure(final ManageMeasureDetailModel model, final Measure measure) {
		logger.info("In MeasureLibraryServiceImpl.createMeasureDetailsModelFromMeasure()");
		model.setId(measure.getId());
		model.setName(measure.getDescription());
		model.setShortName(measure.getaBBRName());
		model.setMeasScoring(measure.getMeasureScoring());
		model.setOrgVersionNumber(MeasureUtility.formatVersionText(measure.getRevisionNumber(),
				String.valueOf(measure.getVersionNumber())));
		model.setVersionNumber(MeasureUtility.getVersionText(model.getOrgVersionNumber(), measure.isDraft()));
		model.setFinalizedDate(DateUtility.convertDateToString(measure.getFinalizedDate()));
		model.setDraft(measure.isDraft());
		model.setMeasureSetId(measure.getMeasureSet().getId());
		model.setValueSetDate(DateUtility.convertDateToStringNoTime(measure.getValueSetDate()));
		model.seteMeasureId(measure.geteMeasureId());
		/*model.setMeasureStatus(measure.getMeasureStatus());*/
		model.setMeasureOwnerId(measure.getOwner().getId());
		logger.info("Exiting MeasureLibraryServiceImpl.createMeasureDetailsModelFromMeasure()");
	}
	
	/**
	 * Creates the measure details xml.
	 * 
	 * @param measureDetailModel
	 *            the measure detail model
	 * @param measure
	 *            the measure
	 * @return the string
	 */
	public final String createMeasureDetailsXml(final ManageMeasureDetailModel measureDetailModel, final Measure measure) {
		logger.info("In MeasureLibraryServiceImpl.createMeasureDetailsXml()");
		setAdditionalAttrsForMeasureXml(measureDetailModel, measure);
		logger.info("creating XML from Measure Details Model");
		ByteArrayOutputStream stream = createXml(measureDetailModel);
		logger.debug(stream.toString());
		return stream.toString();
	}
	
	/**
	 * Creates the measure xml model.
	 * 
	 * @param manageMeasureDetailModel
	 *            the manage measure detail model
	 * @param measure
	 *            the measure
	 * @param replaceNode
	 *            the replace node
	 * @param parentNode
	 *            the parent node
	 * @return the measure xml model
	 */
	private MeasureXmlModel createMeasureXmlModel(final ManageMeasureDetailModel manageMeasureDetailModel, final Measure measure,
			final String replaceNode, final String parentNode) {
		MeasureXmlModel measureXmlModel = new MeasureXmlModel();
		measureXmlModel.setMeasureId(measure.getId());
		measureXmlModel.setXml(createMeasureDetailsXml(manageMeasureDetailModel, measure));
		measureXmlModel.setToReplaceNode(replaceNode);
		measureXmlModel.setParentNode(parentNode);
		return measureXmlModel;
	}
	
	/**
	 * Method to create XML from QualityDataModelWrapper object.
	 * 
	 * @param qualityDataSetDTO
	 *            - {@link QualityDataModelWrapper}.
	 * @return {@link ByteArrayOutputStream}.
	 * */
	private ByteArrayOutputStream createQDMXML(final QualityDataModelWrapper qualityDataSetDTO) {
		logger.info("In ManageCodeLiseServiceImpl.createXml()");
		Mapping mapping = new Mapping();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try {
			mapping.loadMapping(new ResourceLoader().getResourceAsURL("QualityDataModelMapping.xml"));
			Marshaller marshaller = new Marshaller(new OutputStreamWriter(stream));
			marshaller.setMapping(mapping);
			marshaller.marshal(qualityDataSetDTO);
			logger.info("Marshalling of QualityDataSetDTO is successful..");
		} catch (Exception e) {
			if (e instanceof IOException) {
				logger.info("Failed to load QualityDataModelMapping.xml" + e);
			} else if (e instanceof MappingException) {
				logger.info("Mapping Failed" + e);
			} else if (e instanceof MarshalException) {
				logger.info("Unmarshalling Failed" + e);
			} else if (e instanceof ValidationException) {
				logger.info("Validation Exception" + e);
			} else {
				logger.info("Other Exception" + e);
			}
		}
		logger.info("Exiting ManageCodeLiseServiceImpl.createXml()");
		return stream;
	}
	
	/**
	 * Creates the xml.
	 * 
	 * @param measureDetailModel
	 *            the measure detail model
	 * @return the byte array output stream
	 */
	private ByteArrayOutputStream createXml(final ManageMeasureDetailModel measureDetailModel) {
		logger.info("In MeasureLibraryServiceImpl.createXml()");
		Mapping mapping = new Mapping();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try {
			mapping.loadMapping(new ResourceLoader().getResourceAsURL("MeasureDetailsModelMapping.xml"));
			Marshaller marshaller = new Marshaller(new OutputStreamWriter(stream));
			marshaller.setMapping(mapping);
			marshaller.marshal(measureDetailModel);
			logger.info("Marshalling of ManageMeasureDetailsModel is successful..");
			logger.debug("Marshalling of ManageMeasureDetailsModel is successful.." + stream.toString());
		} catch (Exception e) {
			if (e instanceof IOException) {
				logger.info("Failed to load MeasureDetailsModelMapping.xml" + e);
			} else if (e instanceof MappingException) {
				logger.info("Mapping Failed" + e);
			} else if (e instanceof MarshalException) {
				logger.info("Unmarshalling Failed" + e);
			} else if (e instanceof ValidationException) {
				logger.info("Validation Exception" + e);
			} else {
				logger.info("Other Exception" + e);
			}
		}
		logger.info("Exiting MeasureLibraryServiceImpl.createXml()");
		return stream;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#deleteMeasureNotes(mat.DTO.MeasureNoteDTO)
	 */
	@Override
	public final void deleteMeasureNotes(final MeasureNoteDTO measureNoteDTO) {
		MeasureNotesDAO measureNotesDAO = getMeasureNotesDAO();
		MeasureNotes measureNotes = measureNotesDAO.find(measureNoteDTO.getId());
		try {
			getMeasureNotesService().deleteMeasureNote(measureNotes);
			logger.info("MeasureNotes Deleted Successfully :: " + measureNotes.getId());
		} catch (Exception e) {
			logger.info("MeasureNotes not deleted. Exception occured. Measure notes Id :: " + measureNotes.getId());
		}
	}
	
	/**
	 * Extract measure search model detail.
	 * 
	 * @param currentUserId
	 *            - {@link String}
	 * @param isSuperUser
	 *            - {@link Boolean}
	 * @param dto
	 *            - {@link MeasureShareDTO}.
	 * @return {@link Result}.
	 */
	private ManageMeasureSearchModel.Result extractMeasureSearchModelDetail(final String currentUserId, final boolean isSuperUser,
			final MeasureShareDTO dto) {
		boolean isOwner = currentUserId.equals(dto.getOwnerUserId());
		ManageMeasureSearchModel.Result detail = new ManageMeasureSearchModel.Result();
		Measure measure = getMeasureDAO().find(dto.getMeasureId());
		detail.setName(dto.getMeasureName());
		detail.setShortName(dto.getShortName());
		detail.setScoringType(dto.getScoringType());
		detail.setStatus(dto.getStatus());
		detail.setId(dto.getMeasureId());
		detail.setStatus(dto.getStatus());
		detail.seteMeasureId(dto.geteMeasureId());
		detail.setClonable(isOwner || isSuperUser);
		detail.setEditable((isOwner || isSuperUser || ShareLevel.MODIFY_ID.equals(dto.getShareLevel())) && dto.isDraft());
		detail.setExportable(dto.isPackaged());
		detail.setHQMFR1((measure.getExportedDate() != null) && measure.getExportedDate()
				.before(getFormattedReleaseDate(releaseDate)));
		detail.setHQMFR2((measure.getExportedDate() != null) && (measure.getExportedDate()
				.after(getFormattedReleaseDate(releaseDate))
				|| measure.getExportedDate().equals(getFormattedReleaseDate(releaseDate))));
		detail.setSharable(isOwner || isSuperUser);
		detail.setMeasureLocked(dto.isLocked());
		detail.setLockedUserInfo(dto.getLockedUserInfo());
		User user = getUserService().getById(dto.getOwnerUserId());
		detail.setOwnerfirstName(user.getFirstName());
		detail.setOwnerLastName(user.getLastName());
		detail.setOwnerEmailAddress(user.getEmailAddress());
		detail.setDraft(dto.isDraft());
		String formattedVersion = MeasureUtility.getVersionTextWithRevisionNumber(dto.getVersion(), dto.getRevisionNumber(), dto.isDraft());
		detail.setVersion(formattedVersion);
		detail.setFinalizedDate(dto.getFinalizedDate());
		detail.setMeasureSetId(dto.getMeasureSetId());
		return detail;
	}
	
	/**
	 * Find out maximum version number.
	 * 
	 * @param measureSetId
	 *            - {@link String}.
	 * @return {@link String}. *
	 */
	private String findOutMaximumVersionNumber(final String measureSetId) {
		String maxVerNum = getService().findOutMaximumVersionNumber(measureSetId);
		return maxVerNum;
	}
	
	/**
	 * Find out version number.
	 * 
	 * @param measureId
	 *            - {@link String}.
	 * @param measureSetId
	 *            - {@link String}.
	 * @return {@link String}. *
	 */
	private String findOutVersionNumber(final String measureId, final String measureSetId) {
		String maxVerNum = getService().findOutVersionNumber(measureId, measureSetId);
		return maxVerNum;
	}
	
	/**
	 * * Find All QDM's which are used in Clause Workspace tag's or in
	 * Supplemental Data Elements or in Attribute tags.
	 * 
	 * @param appliedQDMList
	 *            the applied qdm list
	 * @param measureXmlModel
	 *            the measure xml model
	 * @return the array list
	 */
	private ArrayList<QualityDataSetDTO> findUsedQDMs(final ArrayList<QualityDataSetDTO> appliedQDMList,
			final MeasureXmlModel measureXmlModel) {
		
		XmlProcessor processor = new XmlProcessor(measureXmlModel.getXml());
		javax.xml.xpath.XPath xPath = XPathFactory.newInstance().newXPath();
		for (QualityDataSetDTO dataSetDTO : appliedQDMList) {
			String XPATH_EXPRESSION = "/measure//subTree//elementRef/@id=";
			XPATH_EXPRESSION = XPATH_EXPRESSION.concat("'").concat(dataSetDTO.getUuid()).
					concat("' or /measure//subTree//elementRef/attribute/@qdmUUID= '").concat(dataSetDTO.getUuid()).
					concat("' or /measure/supplementalDataElements//@id='").concat(dataSetDTO.getUuid())
					.concat("' or /measure/measureDetails/itemCount//@id='").concat(dataSetDTO.getUuid())
					.concat("' or /measure//measureGrouping//packageClause//elementRef/@id='").concat(dataSetDTO.getUuid())
					.concat("'");
			
			try {
				Boolean isUsed = (Boolean) xPath.evaluate(XPATH_EXPRESSION, processor.getOriginalDoc().getDocumentElement(),
						XPathConstants.BOOLEAN);
				dataSetDTO.setUsed(isUsed);
			} catch (XPathExpressionException e) {
				
				e.printStackTrace();
			}
		}
		
		return appliedQDMList;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#generateAndSaveMaxEmeasureId(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public final int generateAndSaveMaxEmeasureId(final ManageMeasureDetailModel measureModel) {
		MeasurePackageService service = getService();
		Measure meas = service.getById(measureModel.getId());
		int eMeasureId = service.saveAndReturnMaxEMeasureId(meas);
		measureModel.seteMeasureId(eMeasureId);
		saveMaxEmeasureIdinMeasureXML(measureModel);
		return eMeasureId;
	}
	
	/**
	 * Save max emeasure idin measure xml.
	 *
	 * @param measureModel the measure model
	 */
	public void saveMaxEmeasureIdinMeasureXML(ManageMeasureDetailModel measureModel){
		
		MeasureXmlModel model = getMeasureXmlForMeasure(measureModel.getId());
		XmlProcessor xmlProcessor = new XmlProcessor(model.getXml());
		
		try {
			
			xmlProcessor.createEmeasureIdNode(measureModel.geteMeasureId());
			String newXml = xmlProcessor.transform(xmlProcessor.getOriginalDoc());
			model.setXml(newXml);
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		
		getService().saveMeasureXml(model);
	}
	
	/**
	 * Gets the all data type attributes.
	 * 
	 * @param qdmName
	 *            - {@link String}.
	 * @return {@link List} of {@link QDSAttributes}. *
	 */
	private List<QDSAttributes> getAllDataTypeAttributes(final String qdmName) {
		List<QDSAttributes> attrs = getAttributeDAO().findByDataType(qdmName, context);
		List<QDSAttributes> attrs1 = getAttributeDAO().getAllDataFlowAttributeName();
		Collections.sort(attrs, attributeComparator);
		Collections.sort(attrs1, attributeComparator);
		attrs.addAll(attrs1);
		// Collections.sort(attrs, attributeComparator);
		return attrs;
	}
	
	/**
	 * Check if qdm data type is present.
	 *
	 * @param dataTypeName the data type name
	 * @return true, if successful
	 */
	public boolean checkIfQDMDataTypeIsPresent(String dataTypeName){
		boolean checkIfDataTypeIsPresent = false;
		DataTypeDAO dataTypeDAO = (DataTypeDAO)context.getBean("dataTypeDAO");
		DataType dataType = dataTypeDAO.findByDataTypeName(dataTypeName);
		if(dataType!=null){
			checkIfDataTypeIsPresent = true;
		}
		return checkIfDataTypeIsPresent;
	}
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getAllMeasureNotesByMeasureID(java.lang.String)
	 */
	@Override
	public final MeasureNotesModel getAllMeasureNotesByMeasureID(final String measureID) {
		MeasureNotesModel measureNotesModel = new MeasureNotesModel();
		ArrayList<MeasureNoteDTO> data = new ArrayList<MeasureNoteDTO>();
		Measure measure = getMeasureDAO().find(measureID);
		if (measure != null) {
			List<MeasureNotes> measureNotesList = getMeasureNotesService().getAllMeasureNotesByMeasureID(measureID);
			if ((measureNotesList != null) && !measureNotesList.isEmpty()) {
				for (MeasureNotes measureNotes : measureNotesList) {
					if (measureNotes != null) {
						MeasureNoteDTO measureNoteDTO = new MeasureNoteDTO();
						measureNoteDTO.setMeasureId(measureID);
						measureNoteDTO.setId(measureNotes.getId());
						if (measureNotes.getModifyUser() != null) {
							measureNoteDTO.setLastModifiedByEmailAddress(
									measureNotes.getModifyUser().getEmailAddress());
						} else if (measureNotes.getCreateUser() != null) {
							measureNoteDTO.setLastModifiedByEmailAddress(
									measureNotes.getCreateUser().getEmailAddress());
						}
						measureNoteDTO.setNoteDesc(measureNotes.getNoteDesc());
						measureNoteDTO.setNoteTitle(measureNotes.getNoteTitle());
						Date lastModifiedDate = measureNotes.getLastModifiedDate();
						SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a z");
						if (lastModifiedDate != null) {
							measureNoteDTO.setLastModifiedDate(dateFormat.format(lastModifiedDate));
						}
						data.add(measureNoteDTO);
					}
				}
			}
		}
		measureNotesModel.setData(data);
		return measureNotesModel;
	}
	/*
	 * (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getAllRecentMeasureForUser(java.lang.String)
	 */
	/** Method to retrieve all Recently searched measure's for the logged in User from 'Recent_MSR_Activity_Log' table.
	 * @param userId - String logged in user id.
	 * @return {@link ManageMeasureSearchModel}. **/
	@Override
	public ManageMeasureSearchModel getAllRecentMeasureForUser(String userId) {
		// Call to fetch
		ArrayList<RecentMSRActivityLog> recentMeasureActivityList = (ArrayList<RecentMSRActivityLog>)
				recentMSRActivityLogDAO.getRecentMeasureActivityLog(userId);
		ManageMeasureSearchModel manageMeasureSearchModel = new ManageMeasureSearchModel();
		List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
		manageMeasureSearchModel.setData(detailModelList);
		String currentUserId = LoggedInUserUtil.getLoggedInUser();
		String userRole = LoggedInUserUtil.getLoggedInUserRole();
		boolean isSuperUser = SecurityRole.SUPER_USER_ROLE.equals(userRole);
		for (RecentMSRActivityLog activityLog : recentMeasureActivityList) {
			Measure measure = getMeasureDAO().find(activityLog.getMeasureId());
			ManageMeasureSearchModel.Result detail = new ManageMeasureSearchModel.Result();
			detail.setName(measure.getDescription());
			detail.setShortName(measure.getaBBRName());
			detail.setId(measure.getId());
			detail.setDraft(measure.isDraft());
			detail.setExportable(measure.getExportedDate() != null); // to show export icon.
			detail.setHQMFR1((measure.getExportedDate() != null) && measure.getExportedDate()
					.before(getFormattedReleaseDate(releaseDate)));
			detail.setHQMFR2((measure.getExportedDate() != null) && (measure.getExportedDate()
					.after(getFormattedReleaseDate(releaseDate))
					|| measure.getExportedDate().equals(getFormattedReleaseDate(releaseDate))));
			/*detail.setStatus(measure.getMeasureStatus());*/
			String formattedVersion = MeasureUtility.getVersionTextWithRevisionNumber(measure.getVersion(), measure.getRevisionNumber(),
					measure.isDraft());
			detail.setVersion(formattedVersion);
			detail.setFinalizedDate(measure.getFinalizedDate());
			detail.setOwnerfirstName(measure.getOwner().getFirstName());
			detail.setOwnerLastName(measure.getOwner().getLastName());
			detail.setOwnerEmailAddress(measure.getOwner().getEmailAddress());
			detail.setMeasureSetId(measure.getMeasureSet().getId());
			detail.setScoringType(measure.getMeasureScoring());
			boolean isLocked = getMeasureDAO().isMeasureLocked(measure.getId());
			detail.setMeasureLocked(isLocked);
			// Prod issue fixed - Measure Shared with Regular users not loaded as editable measures.
			List<MeasureShareDTO> measureShare = getMeasureDAO().
					getMeasureShareInfoForMeasureAndUser(currentUserId, measure.getId());
			if (measureShare.size() > 0) {
				detail.setEditable(((currentUserId.equals(measure.getOwner().getId()) || isSuperUser
						|| ShareLevel.MODIFY_ID.equals(
								measureShare.get(0).getShareLevel()))) && measure.isDraft());
			} else {
				detail.setEditable((currentUserId.equals(measure.getOwner().getId()) || isSuperUser)
						&& measure.isDraft());
			}
			if (isLocked && (measure.getLockedUser() != null)) {
				LockedUserInfo lockedUserInfo = new LockedUserInfo();
				lockedUserInfo.setUserId(measure.getLockedUser().getId());
				lockedUserInfo.setEmailAddress(measure.getLockedUser()
						.getEmailAddress());
				lockedUserInfo.setFirstName(measure.getLockedUser().getFirstName());
				lockedUserInfo.setLastName(measure.getLockedUser().getLastName());
				detail.setLockedUserInfo(lockedUserInfo);
			}
			detailModelList.add(detail);
		}
		return manageMeasureSearchModel;
	}
	
	/** Gets the and validate value set date.
	 * 
	 * @param valueSetDateStr - {@link String}.
	 * @return the and validate value set date
	 * @throws InvalidValueSetDateException - {@link Exception}. * */
	private void getAndValidateValueSetDate(final String valueSetDateStr) throws InvalidValueSetDateException {
		if (StringUtils.isNotBlank(valueSetDateStr)) {
			DateStringValidator dsv = new DateStringValidator();
			int validationCode = dsv.isValidDateString(valueSetDateStr);
			if (validationCode != DateStringValidator.VALID) {
				throw new InvalidValueSetDateException();
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getAppliedQDMFromMeasureXml(java.lang.String, boolean)
	 */
	@Override
	public final ArrayList<QualityDataSetDTO> getAppliedQDMFromMeasureXml(final String measureId,
			final boolean checkForSupplementData) {
		MeasureXmlModel measureXmlModel = getMeasureXmlForMeasure(measureId);
		QualityDataModelWrapper details = convertXmltoQualityDataDTOModel(measureXmlModel);
		ArrayList<QualityDataSetDTO> finalList = new ArrayList<QualityDataSetDTO>();
		if (details != null) {
			if ((details.getQualityDataDTO() != null) && (details.getQualityDataDTO().size() != 0)) {
				logger.info(" details.getQualityDataDTO().size() :" + details.getQualityDataDTO().size());
				for (QualityDataSetDTO dataSetDTO : details.getQualityDataDTO()) {
					if (dataSetDTO.getCodeListName() != null) {
						if ((checkForSupplementData && dataSetDTO.isSuppDataElement())) {
							continue;
						} else {
							finalList.add(dataSetDTO);
						}
					}
				}
			}
			Collections.sort(finalList, new Comparator<QualityDataSetDTO>() {
				@Override
				public int compare(final QualityDataSetDTO o1, final QualityDataSetDTO o2) {
					return o1.getCodeListName().compareToIgnoreCase(o2.getCodeListName());
				}
			});
		}
		
		finalList = findUsedQDMs(finalList, measureXmlModel);
		logger.info("finalList()of QualityDataSetDTO ::" + finalList.size());
		return finalList;
		
	}
	
	/**
	 * Gets the attribute dao.
	 * 
	 * @return the attribute dao
	 */
	private QDSAttributesDAO getAttributeDAO() {
		return ((QDSAttributesDAO) context.getBean("qDSAttributesDAO"));
		
	}
	
	/**
	 * Gets the context.
	 * 
	 * @return the context
	 */
	public ApplicationContext getContext() {
		return context;
	}
	
	/**
	 * Gets the locked user.
	 * 
	 * @param existingMeasure
	 *            the existing measure
	 * @return the locked user
	 */
	private User getLockedUser(final Measure existingMeasure) {
		return existingMeasure.getLockedUser();
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getMaxEMeasureId()
	 */
	@Override
	public final int getMaxEMeasureId() {
		MeasurePackageService service = getService();
		int emeasureId = service.getMaxEMeasureId();
		logger.info("**********Current Max EMeasure Id from DB ******************" + emeasureId);
		return emeasureId;
		// return 2012;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getMeasure(java.lang.String)
	 */
	@Override
	public final ManageMeasureDetailModel getMeasure(final String key) {
		logger.info("In MeasureLibraryServiceImpl.getMeasure() method..");
		logger.info("Loading Measure for MeasueId: " + key);
		Measure measure = getService().getById(key);
		MeasureXmlModel xml = getMeasureXmlForMeasure(key);
		return convertXmltoModel(xml, measure);
		
	}
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#updateComponentMeasuresOnDeletion(java.lang.String
	 */
	@Override
	public void updateMeasureXmlForDeletedComponentMeasureAndOrg(String measureId){
		logger.info("In MeasureLibraryServiceImpl. updateMeasureXmlForDeletedComponentMeasureAndOrg() method..");
		logger.info("Updating Measure for MeasueId: " + measureId);
		MeasureXmlModel xmlModel = getMeasureXmlForMeasure(measureId);
		if(xmlModel!=null){
			XmlProcessor processor = new XmlProcessor(xmlModel.getXml());
			removeDeletedComponentMeasures(processor);
			removeDeletedSteward(processor);
			removeDeletedDevelopers(processor);
			xmlModel.setXml(processor.transform(processor.getOriginalDoc()));
			getService().saveMeasureXml(xmlModel);
		}
		
	}
	
	/**
	 * Update measure developers on deletion.
	 *
	 * @param processor the processor
	 */
	private void removeDeletedDevelopers(XmlProcessor processor) {
		try {
			NodeList developerParentNodeList = (NodeList) xPath.evaluate(
					XPATH_EXPRESSION_DEVELOPERS, processor.getOriginalDoc(),
					XPathConstants.NODESET);
			Node developerParentNode = developerParentNodeList.item(0);
			if (developerParentNode != null) {
				NodeList developerNodeList = developerParentNode
						.getChildNodes();
				for (int i = 0; i < developerNodeList.getLength(); i++) {
					Node newNode = developerNodeList.item(i);
					String developerId = newNode.getAttributes()
							.getNamedItem("id").getNodeValue();
					Organization org = organizationDAO.findById(developerId);
					if (org == null) {
						developerParentNode.removeChild(newNode);
						logger.info("Deleted MeasureDevelopers Deleted successFully From MeasureXml.");
					} else {
						newNode.setTextContent(org.getOrganizationName());
						logger.info("Developer's Name updated in MeasureXml.");
					}
				}
			}
		} catch (XPathExpressionException e) {
			logger.info("Failed to delete  MeasureDevelopers From MeasureXml. Exception occured.");
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Update steward on deletion.
	 *
	 * @param processor the processor
	 */
	private void removeDeletedSteward(XmlProcessor processor) {
		try {
			// steward
			Node stewardParentNode = (Node) xPath.evaluate(
					XPATH_EXPRESSION_STEWARD, processor.getOriginalDoc(),
					XPathConstants.NODE);
			if (stewardParentNode != null) {
				String id = stewardParentNode.getAttributes()
						.getNamedItem("id").getNodeValue();
				Organization org = organizationDAO.findById(id);
				if (org == null) {
					removeNode(XPATH_EXPRESSION_STEWARD,
							processor.getOriginalDoc());
					logger.info("Deleted steward Deleted successFully From MeasureXml.");
				} else {
					stewardParentNode.setTextContent(org.getOrganizationName());
					logger.info("Steward Name updated in measure Xml.");
				}
			}
		} catch (XPathExpressionException e) {
			logger.info("Failed to delete  steward From MeasureXml. Exception occured.");
			e.printStackTrace();
		}
		
	}
	/**
	 * Update component measures on deletion.
	 *
	 * @param processor the processor
	 */
	private void removeDeletedComponentMeasures(XmlProcessor processor) {
		try {
			NodeList componentMeasureParentNodeList = (NodeList) xPath
					.evaluate(XPATH_EXPRESSION_COMPONENT_MEASURES,
							processor.getOriginalDoc(), XPathConstants.NODESET);
			Node componentMeasureParentNode = componentMeasureParentNodeList
					.item(0);
			if (componentMeasureParentNode != null) {
				NodeList nodeList = componentMeasureParentNode.getChildNodes();
				
				for (int i = 0; i < nodeList.getLength(); i++) {
					Node newNode = nodeList.item(i);
					String id = newNode.getAttributes().getNamedItem("id")
							.getNodeValue();
					boolean isDeleted = getMeasureDAO().getMeasure(id);
					if (!isDeleted) {
						componentMeasureParentNode.removeChild(newNode);
					}
				}
			}
			logger.info("Deleted componentMeasures Deleted successFully From MeasureXml.");
		} catch (XPathExpressionException e) {
			logger.info("Failed to delete  componentMeasures From MeasureXml. Exception occured.");
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets the measure dao.
	 * 
	 * @return the measure dao
	 */
	private MeasureDAO getMeasureDAO() {
		return ((MeasureDAO) context.getBean("measureDAO"));
	}
	
	/**
	 * Gets the measure notes dao.
	 * 
	 * @return the measure notes dao
	 */
	private MeasureNotesDAO getMeasureNotesDAO() {
		return ((MeasureNotesDAO) context.getBean("measureNotesDAO"));
	}
	
	/**
	 * Gets the measure notes service.
	 * 
	 * @return the measure notes service
	 */
	private MeasureNotesService getMeasureNotesService() {
		return ((MeasureNotesService) context.getBean("measureNotesService"));
		
	}
	
	/**
	 * Gets the measure package service.
	 * 
	 * @return {@link MeasurePackageService}. *
	 */
	public final MeasurePackageService getMeasurePackageService() {
		return (MeasurePackageService) context.getBean("measurePackageService");
	}
	
	/**
	 * Gets the measure xml dao.
	 * 
	 * @return the measure xml dao
	 */
	private MeasureXMLDAO getMeasureXMLDAO() {
		return ((MeasureXMLDAO) context.getBean("measureXMLDAO"));
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getMeasureXmlForMeasure(java.lang.String)
	 */
	@Override
	public final MeasureXmlModel getMeasureXmlForMeasure(final String measureId) {
		logger.info("In MeasureLibraryServiceImpl.getMeasureXmlForMeasure()");
		MeasureXmlModel measureXmlModel = getService().getMeasureXmlForMeasure(measureId);
		if (measureXmlModel == null) {
			logger.info("Measure XML is null");
		} else {
			logger.debug("XML ::: " + measureXmlModel.getXml());
		}
		return measureXmlModel;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getMeasureXmlForMeasureAndSortedSubTreeMap(java.lang.String)
	 */
	@Override
	public SortedClauseMapResult getMeasureXmlForMeasureAndSortedSubTreeMap(final String measureId){
		SortedClauseMapResult result = new SortedClauseMapResult();
		MeasureXmlModel model = getMeasureXmlForMeasure(measureId);
		LinkedHashMap<String, String> sortedSubTreeMap = getSortedClauseMap(measureId);
		result.setMeasureXmlModel(model);
		result.setClauseMap(sortedSubTreeMap);
		return result;
	}
	
	/**
	 * Gets the sorted clause map.
	 *
	 * @param measureId the measure id
	 * @return the sorted clause map
	 */
	@Override
	public LinkedHashMap<String, String> getSortedClauseMap(String measureId){
		
		logger.info("In MeasureLibraryServiceImpl.getSortedClauseMap()");
		MeasureXmlModel measureXmlModel = getService().getMeasureXmlForMeasure(
				measureId);
		
		LinkedHashMap<String, String> sortedMainClauseMap = new LinkedHashMap<String, String>();
		LinkedHashMap<String, String> mainClauseMap = new LinkedHashMap<String, String>();
		
		if ((measureXmlModel != null)
				&& StringUtils.isNotBlank(measureXmlModel.getXml())) {
			XmlProcessor xmlProcessor = new XmlProcessor(
					measureXmlModel.getXml());
			NodeList mainClauseLIst;
			NodeList instanceClauseList;
			try {
				mainClauseLIst = (NodeList) xPath.evaluate(
						"/measure//subTreeLookUp/subTree[not(@instanceOf )]",
						xmlProcessor.getOriginalDoc().getDocumentElement(),
						XPathConstants.NODESET);
				for (int i = 0; i < mainClauseLIst.getLength(); i++) {
					mainClauseMap.put(mainClauseLIst.item(i).getAttributes()
							.getNamedItem("displayName").getNodeValue(),
							mainClauseLIst.item(i).getAttributes()
							.getNamedItem("uuid").getNodeValue());
				}
				// sort the map alphabetically
				List<Entry<String, String>> mainClauses = new LinkedList<Map.Entry<String, String>>(
						mainClauseMap.entrySet());
				Collections.sort(mainClauses,
						new Comparator<Entry<String, String>>() {
					@Override
					public int compare(Entry<String, String> o1,
							Entry<String, String> o2) {
						return o1.getKey().toUpperCase()
								.compareTo(o2.getKey().toUpperCase());
					}
				});
				for (Entry<String, String> entry : mainClauses) {
					sortedMainClauseMap.put(entry.getValue(), entry.getKey());
					
					instanceClauseList = (NodeList) xPath.evaluate(
							"/measure//subTreeLookUp/subTree[@instanceOf='"
									+ entry.getValue() + "']", xmlProcessor
									.getOriginalDoc().getDocumentElement(),
									XPathConstants.NODESET);
					
					if (instanceClauseList.getLength() >= 1) {
						
						Map<String, String> instanceClauseMap = new HashMap<String, String>();
						for (int j = 0; j < instanceClauseList.getLength(); j++) {
							String uuid = instanceClauseList.item(j)
									.getAttributes().getNamedItem("uuid")
									.getNodeValue();
							String name = instanceClauseList.item(j)
									.getAttributes()
									.getNamedItem("displayName").getNodeValue();
							String instanceVal = instanceClauseList.item(j)
									.getAttributes().getNamedItem("instance")
									.getNodeValue().toUpperCase();
							String fname = "Occurrence "+ instanceVal +" of "
									+ name;
							instanceClauseMap.put(fname, uuid);
						}
						
						List<Entry<String, String>> instanceClauses = new LinkedList<Map.Entry<String, String>>(
								instanceClauseMap.entrySet());
						Collections.sort(instanceClauses,
								new Comparator<Entry<String, String>>() {
							@Override
							public int compare(
									Entry<String, String> o1,
									Entry<String, String> o2) {
								return o1
										.getKey()
										.toUpperCase()
										.compareTo(
												o2.getKey()
												.toUpperCase());
							}
						});
						for (Entry<String, String> entry1 : instanceClauses) {
							sortedMainClauseMap.put(entry1.getValue(),
									entry1.getKey());
						}
					}
				}
			} catch (XPathExpressionException e) {
				e.printStackTrace();
			}
			
		}
		return sortedMainClauseMap;
	}
	
	/** Gets the page count.
	 * 
	 * @param userId the user id
	 * @return {@link Integer}. * */
	/*private int getPageCount(final long totalRows, final int numberOfRows) {
		int pageCount = 0;
		int mod = (int) (totalRows % numberOfRows);
		pageCount = (int) (totalRows / numberOfRows);
		pageCount = (mod > 0) ? (pageCount + 1) : pageCount;
		return pageCount;
	}*/
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getRecentMeasureActivityLog(java.lang.String)
	 */
	@Override
	public List<RecentMSRActivityLog> getRecentMeasureActivityLog(String userId) {
		return recentMSRActivityLogDAO.getRecentMeasureActivityLog(userId);
	}
	
	/**
	 * Gets the service.
	 * 
	 * @return the service
	 */
	private MeasurePackageService getService() {
		return (MeasurePackageService) context.getBean("measurePackageService");
	}
	
	/**
	 * Gets the user service.
	 * 
	 * @return the user service
	 */
	private UserService getUserService() {
		return (UserService) context.getBean("userService");
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getUsersForShare(java.lang.String, int, int)
	 */
	@Override
	public final ManageMeasureShareModel getUsersForShare(final String measureId, final int startIndex, final int pageSize) {
		ManageMeasureShareModel model = new ManageMeasureShareModel();
		List<MeasureShareDTO> dtoList = getService().getUsersForShare(measureId, startIndex, pageSize);
		model.setResultsTotal(getService().countUsersForMeasureShare());
		List<MeasureShareDTO> dataList = new ArrayList<MeasureShareDTO>();
		for (MeasureShareDTO dto : dtoList) {
			dataList.add(dto);
		}
		// model.setData(dtoList); Directly setting dtoList causes the RPC
		// serialization exception(java.util.RandomaccessSubList) since we are
		// sublisting it.
		model.setData(dataList);
		model.setStartIndex(startIndex);
		model.setMeasureId(measureId);
		model.setPrivate(getService().getById(measureId).getIsPrivate());
		return model;
	}
	
	/**
	 * Increment version number and save.
	 * 
	 * @param maximumVersionNumber
	 *            - {@link String}.
	 * @param incrementBy
	 *            - {@link String}.
	 * @param mDetail
	 *            - {@link ManageMeasureDetailModel}.
	 * @param meas
	 *            - {@link Measure}.
	 * @return {@link SaveMeasureResult}. *
	 */
	private SaveMeasureResult incrementVersionNumberAndSave(final String maximumVersionNumber, final String incrementBy,
			final ManageMeasureDetailModel mDetail, final Measure meas) {
		BigDecimal mVersion = new BigDecimal(maximumVersionNumber);
		mVersion = mVersion.add(new BigDecimal(incrementBy));
		mDetail.setVersionNumber(mVersion.toString());
		Date currentDate = new Date();
		mDetail.setFinalizedDate(DateUtility.convertDateToString(currentDate));
		mDetail.setDraft(false);
		setValueFromModel(mDetail, meas);
		getService().save(meas);
		saveMeasureXml(createMeasureXmlModel(mDetail, meas, MEASURE_DETAILS, MEASURE));
		SaveMeasureResult result = new SaveMeasureResult();
		result.setSuccess(true);
		result.setId(meas.getId());
		String versionStr = meas.getMajorVersionStr() + "." + meas.getMinorVersionStr();
		result.setVersionStr(versionStr);
		logger.info("Result passed for Version Number " + versionStr);
		return result;
	}
	
	// TODO refactor this logic into a shared location: see MeasureDAO.
	/**
	 * Checks if Measure is locked.
	 * 
	 * @param m
	 *            the Measure
	 * @return true, if is locked
	 */
	private boolean isLocked(final Measure m) {
		if (m.getLockedOutDate() == null) {
			return false;
		}
		long lockTime = m.getLockedOutDate().getTime();
		long currentTime = System.currentTimeMillis();
		long threshold = 3 * 60 * 1000;
		boolean isLockExpired = (currentTime - lockTime) > threshold;
		return !isLockExpired;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#isMeasureLocked(java.lang.String)
	 */
	@Override
	public final boolean isMeasureLocked(final String id) {
		MeasurePackageService service = getService();
		boolean isLocked = service.isMeasureLocked(id);
		return isLocked;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#recordRecentMeasureActivity(java.lang.String, java.lang.String)
	 */
	@Override
	public void recordRecentMeasureActivity(String measureId, String userId) {
		recentMSRActivityLogDAO.recordRecentMeasureActivity(measureId, userId);
	}
	
	/**
	 * Removes the pattern from xml string.
	 * 
	 * @param xmlString
	 *            the xml string
	 * @param patternStart
	 *            the pattern start
	 * @param replaceWith
	 *            the replace with
	 * @return the string
	 */
	private String removePatternFromXMLString(final String xmlString, final String patternStart, final String replaceWith) {
		String newString = xmlString;
		if (patternStart != null) {
			newString = newString.replaceAll(patternStart, replaceWith);
		}
		return newString;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * mat.client.measure.service.MeasureService#resetLockedDate(java.lang.String
	 * , java.lang.String) This method has been added to release the Measure
	 * lock. It gets the existingMeasureLock and checks the loggedinUserId and
	 * the LockedUserid to release the lock.
	 */
	@Override
	public final SaveMeasureResult resetLockedDate(final String measureId, final String userId) {
		Measure existingMeasure = null;
		User lockedUser = null;
		SaveMeasureResult result = new SaveMeasureResult();
		if ((measureId != null) && (userId != null) && StringUtils.isNotBlank(measureId)) {
			existingMeasure = getService().getById(measureId);
			if (existingMeasure != null) {
				lockedUser = getLockedUser(existingMeasure);
				if ((lockedUser != null) && lockedUser.getId().equalsIgnoreCase(userId)) {
					// Only if the lockedUser and loggedIn User are same we can
					// allow the user to unlock the measure.
					if (existingMeasure.getLockedOutDate() != null) {
						// if it is not null then set it to null and save it.
						existingMeasure.setLockedOutDate(null);
						existingMeasure.setLockedUser(null);
						getService().updateLockedOutDate(existingMeasure);
						result.setSuccess(true);
					}
				}
			}
			result.setId(existingMeasure.getId());
		}
		
		return result;
	}
	
	/**
	 * Return failure reason.
	 * 
	 * @param rs
	 *            - {@link SaveMeasureResult}.
	 * @param failureReason
	 *            - {@link Integer}.
	 * @return {@link SaveMeasureResult}. *
	 */
	private SaveMeasureResult returnFailureReason(final SaveMeasureResult rs, final int failureReason) {
		rs.setFailureReason(failureReason);
		rs.setSuccess(false);
		return rs;
	}
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveMeasureAtPackage(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public SaveMeasureResult saveMeasureAtPackage(ManageMeasureDetailModel model) {
		Measure measure = getService().getById(model.getId());
		Integer revisionNumber = new Integer(000);
		if ((measure.getRevisionNumber() != null) && StringUtils.isNotEmpty(measure.getRevisionNumber())) {
			revisionNumber = Integer.parseInt(measure.getRevisionNumber());
			revisionNumber = revisionNumber + 1;
			measure.setRevisionNumber(String.format("%03d", revisionNumber));
			model.setRevisionNumber(String.format("%03d", revisionNumber));
		} else {
			revisionNumber = revisionNumber + 1;
			measure.setRevisionNumber(String.format("%03d", revisionNumber));
			model.setRevisionNumber(String.format("%03d", revisionNumber));
		}
		getService().save(measure);
		SaveMeasureResult result = save(model);
		return result;
	}
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#save(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public final SaveMeasureResult save(ManageMeasureDetailModel model) {
		
		Measure pkg = null;
		MeasureSet measureSet = null;
		if (model.getId() != null) {
			setMeasureCreated(true);
			// editing an existing measure
			pkg = getService().getById(model.getId());
			model.setVersionNumber(pkg.getVersion());
			if (pkg.isDraft()) {
				model.setRevisionNumber(pkg.getRevisionNumber());
			} else {
				model.setRevisionNumber("000");
			}
			if (pkg.getMeasureSet().getId() != null) {
				measureSet = getService().findMeasureSet(pkg.getMeasureSet().getId());
			}
			if (!pkg.getMeasureScoring().equalsIgnoreCase(model.getMeasScoring())) {
				// US 194 User is changing the measure scoring. Make sure to
				// delete any groupings for that measure and save.
				getMeasurePackageService().deleteExistingPackages(pkg.getId());
			}
			//updateComponentMeasures(model);
			
		} else {
			// creating a new measure.
			setMeasureCreated(false);
			pkg = new Measure();
			/*model.setMeasureStatus("In Progress");*/
			model.setRevisionNumber("000");
			measureSet = new MeasureSet();
			measureSet.setId(UUID.randomUUID().toString());
			getService().save(measureSet);
		}
		
		pkg.setMeasureSet(measureSet);
		setValueFromModel(model, pkg);
		SaveMeasureResult result = new SaveMeasureResult();
		try {
			getAndValidateValueSetDate(model.getValueSetDate());
			pkg.setValueSetDate(DateUtility.addTimeToDate(pkg.getValueSetDate()));
			getService().save(pkg);
		} catch (InvalidValueSetDateException e) {
			result.setSuccess(false);
			result.setFailureReason(SaveMeasureResult.INVALID_VALUE_SET_DATE);
			result.setId(pkg.getId());
			return result;
		}
		result.setSuccess(true);
		result.setId(pkg.getId());
		saveMeasureXml(createMeasureXmlModel(model, pkg, MEASURE_DETAILS, MEASURE));
		return result;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveAndDeleteMeasure(java.lang.String)
	 */
	@Override
	public final void saveAndDeleteMeasure(final String measureID) {
		logger.info("MeasureLibraryServiceImpl: saveAndDeleteMeasure start : measureId:: " + measureID);
		MeasureDAO measureDAO = getMeasureDAO();
		Measure m = measureDAO.find(measureID);
		
		logger.info("Measure Deletion Started for measure Id :: " + measureID);
		try {
			measureDAO.delete(m);
			logger.info("Measure Deleted Successfully :: " + measureID);
		} catch (Exception e) {
			logger.info("Measure not deleted.Something went wrong for measure Id :: " + measureID);
		}
		
		logger.info("MeasureLibraryServiceImpl: saveAndDeleteMeasure End : measureId:: " + measureID);
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveFinalizedVersion(java.lang.String, boolean, java.lang.String)
	 */
	@Override
	public final SaveMeasureResult saveFinalizedVersion(final String measureId, final boolean isMajor, final String version) {
		logger.info("In MeasureLibraryServiceImpl.saveFinalizedVersion() method..");
		Measure m = getService().getById(measureId);
		logger.info("Measure Loaded for: " + measureId);
		String versionNumber = null;
		if (isMajor) {
			versionNumber = findOutMaximumVersionNumber(m.getMeasureSet().getId());
			// For new measure's only draft entry will be
			// available.findOutMaximumVersionNumber will return null.
			if (versionNumber == null) {
				versionNumber = "0.000";
			}
			logger.info("Max Version Number loaded from DB: " + versionNumber);
		} else {
			int versionIndex = version.indexOf('v');
			logger.info("Min Version number passed from Page Model: " + versionIndex);
			String selectedVersion = version.substring(versionIndex + 1);
			logger.info("Min Version number after trim: " + selectedVersion);
			versionNumber = findOutVersionNumber(m.getMeasureSet().getId(), selectedVersion);
			
		}
		ManageMeasureDetailModel mDetail = getMeasure(measureId);
		// Need to check for logic when to mark a measure as completed.
		//mDetail.setMeasureStatus("Complete");
		SaveMeasureResult rs = new SaveMeasureResult();
		int endIndex = versionNumber.indexOf('.');
		String majorVersionNumber = versionNumber.substring(0, endIndex);
		if (!versionNumber.equalsIgnoreCase(ConstantMessages.MAXIMUM_ALLOWED_VERSION)) {
			String[] versionArr = versionNumber.split("\\.");
			if (isMajor) {
				if (!versionArr[0].equalsIgnoreCase(ConstantMessages.MAXIMUM_ALLOWED_MAJOR_VERSION)) {
					return incrementVersionNumberAndSave(majorVersionNumber, "1", mDetail, m);
				} else {
					return returnFailureReason(rs, SaveMeasureResult.REACHED_MAXIMUM_MAJOR_VERSION);
				}
				
			} else {
				if (!versionArr[1].equalsIgnoreCase(ConstantMessages.MAXIMUM_ALLOWED_MINOR_VERSION)) {
					versionNumber = versionArr[0]+"."+versionArr[1];
					return incrementVersionNumberAndSave(versionNumber, "0.001", mDetail, m);
				} else {
					return returnFailureReason(rs, SaveMeasureResult.REACHED_MAXIMUM_MINOR_VERSION);
				}
			}
		} else {
			return returnFailureReason(rs, SaveMeasureResult.REACHED_MAXIMUM_VERSION);
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveMeasureDetails(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public final SaveMeasureResult saveMeasureDetails(final ManageMeasureDetailModel model) {
		logger.info("In MeasureLibraryServiceImpl.saveMeasureDetails() method..");
		Measure measure = null;
		if (model.getId() != null) {
			setMeasureCreated(true);
			measure = getService().getById(model.getId());
			/*if ((measure.getMeasureStatus() != null) && !measure.getMeasureStatus().
					equalsIgnoreCase(model.getMeasureStatus())) {
				measure.setMeasureStatus(model.getMeasureStatus());*/
			getService().save(measure);
			//}
		}
		model.setRevisionNumber(measure.getRevisionNumber());
		logger.info("Saving Measure_Xml");
		saveMeasureXml(createMeasureXmlModel(model, measure, MEASURE_DETAILS, MEASURE));
		SaveMeasureResult result = new SaveMeasureResult();
		result.setSuccess(true);
		logger.info("Saving of Measure Details Success");
		return result;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveMeasureNote(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public final void saveMeasureNote(final String noteTitle, final String noteDescription,
			final String measureId, final String userId) {
		try {
			MeasureNotes measureNote = new MeasureNotes();
			measureNote.setNoteTitle(noteTitle);
			measureNote.setNoteDesc(noteDescription);
			Measure measure = getMeasureDAO().find(measureId);
			if (measure != null) {
				measureNote.setMeasure_id(measureId);
			}
			User user = getUserService().getById(userId);
			if (user != null) {
				measureNote.setCreateUser(user);
			}
			measureNote.setLastModifiedDate(new Date());
			getMeasureNotesService().saveMeasureNote(measureNote);
			logger.info("MeasureNotes Saved Successfully.");
		} catch (Exception e) {
			logger.info("Failed to save MeasureNotes. Exception occured.");
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#saveMeasureXml(mat.client.clause.clauseworkspace.model.MeasureXmlModel)
	 */
	@Override
	public final void saveMeasureXml(final MeasureXmlModel measureXmlModel) {
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureXmlModel.getMeasureId());
		if ((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml())) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			try{
				String scoringTypeBeforeNewXml = (String) xPath.evaluate(
						"/measure/measureDetails/scoring/@id",
						xmlProcessor.getOriginalDoc().getDocumentElement(), XPathConstants.STRING);
				String newXml = xmlProcessor.replaceNode(measureXmlModel.getXml(), measureXmlModel.getToReplaceNode(),
						measureXmlModel.getParentNode());
				String scoringTypeAfterNewXml = (String) xPath.evaluate(
						"/measure/measureDetails/scoring/@id",
						xmlProcessor.getOriginalDoc().getDocumentElement(), XPathConstants.STRING);
				xmlProcessor.checkForScoringType();
				checkForTimingElementsAndAppend(xmlProcessor);
				if(! scoringTypeBeforeNewXml.equalsIgnoreCase(scoringTypeAfterNewXml)) {
					deleteExistingGroupings(xmlProcessor);
				}
				newXml = xmlProcessor.transform(xmlProcessor.getOriginalDoc());
				measureXmlModel.setXml(newXml);
			} catch (XPathExpressionException e) {
				e.printStackTrace();
			}
		} else {
			XmlProcessor processor = new XmlProcessor(measureXmlModel.getXml());
			processor.addParentNode(MEASURE);
			processor.checkForScoringType();
			checkForTimingElementsAndAppend(processor);
			measureXmlModel.setXml(processor.transform(processor.getOriginalDoc()));
			
			QualityDataModelWrapper wrapper = getMeasureXMLDAO().createSupplimentalQDM(
					measureXmlModel.getMeasureId(), false, null);
			// Object to XML for elementLookUp
			ByteArrayOutputStream streamQDM = XmlProcessor.convertQualityDataDTOToXML(wrapper);
			// Object to XML for supplementalDataElements
			ByteArrayOutputStream streamSuppDataEle = XmlProcessor.convertQDMOToSuppleDataXML(wrapper);
			//Object to xml for RiskAdjustment
			//ByteArrayOutputStream streamRiskAdjVar = XmlProcessor.convertclauseToRiskAdjVarXML(riskAdjWrapper);
			// Remove <?xml> and then replace.
			String filteredString = removePatternFromXMLString(
					streamQDM.toString().substring(streamQDM.toString().indexOf("<measure>", 0)), "<measure>", "");
			filteredString = removePatternFromXMLString(filteredString, "</measure>", "");
			// Remove <?xml> and then replace.
			String filteredStringSupp = removePatternFromXMLString(
					streamSuppDataEle.toString().substring(streamSuppDataEle.toString().
							indexOf("<measure>", 0)), "<measure>", "");
			filteredStringSupp = removePatternFromXMLString(filteredStringSupp, "</measure>", "");
			// Add Supplemental data to elementLoopUp
			String result = callAppendNode(measureXmlModel, filteredString, "qdm", "/measure/elementLookUp");
			measureXmlModel.setXml(result);
			// Add Supplemental data to supplementalDataElements
			result = callAppendNode(measureXmlModel, filteredStringSupp, "elementRef", "/measure/supplementalDataElements");
			measureXmlModel.setXml(result);			
			XmlProcessor processor1 = new XmlProcessor(measureXmlModel.getXml());
			measureXmlModel.setXml(processor1.checkForStratificationAndAdd());
		}
		getService().saveMeasureXml(measureXmlModel);
	}
	
	
	/**
	 * Deletes the existing groupings when scoring type selection is changed and saved.
	 *
	 * @param xmlProcessor the xml processor
	 */
	private void deleteExistingGroupings(XmlProcessor xmlProcessor) {
		NodeList measureGroupingList;
		try {
			measureGroupingList = (NodeList) xPath.evaluate("/measure/measureGrouping/group",
					xmlProcessor.getOriginalDoc().getDocumentElement(), XPathConstants.NODESET);
			for(int i = 0; i<measureGroupingList.getLength(); i++ ) {
				removeNode("/measure/measureGrouping/group",xmlProcessor.getOriginalDoc());
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#search(java.lang.String, int, int, int)
	 */
	@Override
	public final ManageMeasureSearchModel search(final String searchText,
			final int startIndex, final int pageSize, final int filter) {
		String currentUserId = LoggedInUserUtil.getLoggedInUser();
		String userRole = LoggedInUserUtil.getLoggedInUserRole();
		boolean isSuperUser = SecurityRole.SUPER_USER_ROLE.equals(userRole);
		ManageMeasureSearchModel searchModel = new ManageMeasureSearchModel();
		
		if (SecurityRole.ADMIN_ROLE.equals(userRole)) {
			List<MeasureShareDTO> measureList = getService()
					.searchForAdminWithFilter(searchText, 1, Integer.MAX_VALUE,
							filter);
			List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
			List<MeasureShareDTO> measureTotalList = measureList;
			searchModel.setResultsTotal(measureTotalList.size());
			if (pageSize < measureTotalList.size()) {
				measureList = measureTotalList
						.subList(startIndex - 1, pageSize);
			} else if (pageSize > measureList.size()) {
				measureList = measureTotalList.subList(startIndex - 1,
						measureList.size());
			}
			searchModel.setStartIndex(startIndex);
			searchModel.setData(detailModelList);
			
			for (MeasureShareDTO dto : measureList) {
				ManageMeasureSearchModel.Result detail = new ManageMeasureSearchModel.Result();
				detail.setName(dto.getMeasureName());
				detail.setId(dto.getMeasureId());
				detail.seteMeasureId(dto.geteMeasureId());
				detail.setDraft(dto.isDraft());
				String formattedVersion = MeasureUtility.getVersionText(
						dto.getVersion(), dto.isDraft());
				detail.setVersion(formattedVersion);
				detail.setFinalizedDate(dto.getFinalizedDate());
				detail.setStatus(dto.getStatus());
				User user = getUserService().getById(dto.getOwnerUserId());
				detail.setOwnerfirstName(user.getFirstName());
				detail.setOwnerLastName(user.getLastName());
				detail.setOwnerEmailAddress(user.getEmailAddress());
				detail.setMeasureSetId(dto.getMeasureSetId());
				detailModelList.add(detail);
			}
		} else {
			List<MeasureShareDTO> measureList = getService().searchWithFilter(
					searchText, 1, Integer.MAX_VALUE, filter);
			List<MeasureShareDTO> measureTotalList = measureList;
			
			searchModel.setResultsTotal(measureTotalList.size());
			if (pageSize <= measureTotalList.size()) {
				measureList = measureTotalList
						.subList(startIndex - 1, pageSize);
			} else if (pageSize > measureList.size()) {
				measureList = measureTotalList.subList(startIndex - 1,
						measureList.size());
			}
			searchModel.setStartIndex(startIndex);
			List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
			searchModel.setData(detailModelList);
			for (MeasureShareDTO dto : measureList) {
				ManageMeasureSearchModel.Result detail = extractMeasureSearchModelDetail(
						currentUserId, isSuperUser, dto);
				detailModelList.add(detail);
			}
			updateMeasureFamily(detailModelList);
		}
		
		return searchModel;
	}
	
	/**
	 * Update measure family.
	 *
	 * @param detailModelList the detail model list
	 */
	public void updateMeasureFamily(List<ManageMeasureSearchModel.Result> detailModelList){
		boolean isFamily=false;
		if((detailModelList!=null) & (detailModelList.size()>0)){
			for(int i=0;i<detailModelList.size();i++){
				if(i>0){
					if(detailModelList.get(i).getMeasureSetId().equalsIgnoreCase(
							detailModelList.get(i-1).getMeasureSetId())) {
						detailModelList.get(i).setMeasureFamily(!isFamily);
					} else {
						detailModelList.get(i).setMeasureFamily(isFamily);
					}
				}
				else{
					detailModelList.get(i).setMeasureFamily(isFamily);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#searchMeasuresForDraft(java.lang.String)
	 */
	@Override
	public final ManageMeasureSearchModel searchMeasuresForDraft(final String searchText) {
		String currentUserId = LoggedInUserUtil.getLoggedInUser();
		String userRole = LoggedInUserUtil.getLoggedInUserRole();
		boolean isSuperUser = SecurityRole.SUPER_USER_ROLE.equals(userRole);
		ManageMeasureSearchModel searchModel = new ManageMeasureSearchModel();
		List<MeasureShareDTO> measureList = getService().searchMeasuresForDraft(searchText);
		searchModel.setResultsTotal((int) getService().countMeasuresForDraft(searchText));
		List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
		searchModel.setData(detailModelList);
		for (MeasureShareDTO dto : measureList) {
			setDTOtoModel(detailModelList, dto, currentUserId, isSuperUser);
		}
		return searchModel;
	}
	
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#searchMeasuresForVersion(java.lang.String)
	 */
	@Override
	public final ManageMeasureSearchModel searchMeasuresForVersion(final String searchText) {
		String currentUserId = LoggedInUserUtil.getLoggedInUser();
		String userRole = LoggedInUserUtil.getLoggedInUserRole();
		boolean isSuperUser = SecurityRole.SUPER_USER_ROLE.equals(userRole);
		ManageMeasureSearchModel searchModel = new ManageMeasureSearchModel();
		List<MeasureShareDTO> measureList = getService().searchMeasuresForVersion(searchText);
		searchModel.setResultsTotal((int) getService().countMeasuresForVersion(searchText));
		List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
		searchModel.setData(detailModelList);
		
		for (MeasureShareDTO dto : measureList) {
			setDTOtoModel(detailModelList, dto, currentUserId, isSuperUser);
		}
		return searchModel;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#searchUsers(int, int)
	 */
	@Override
	public final TransferMeasureOwnerShipModel searchUsers(final String searchText, final int startIndex, final int pageSize) {
		UserService usersService = getUserService();
		List<User> searchResults;
		if(searchText.equals("")){
			searchResults = usersService.searchNonAdminUsers("", startIndex, pageSize);
		}
		else{
			searchResults = usersService.searchNonAdminUsers(searchText, startIndex, pageSize);
		}
		logger.info("User search returned " + searchResults.size());
		
		TransferMeasureOwnerShipModel result = new TransferMeasureOwnerShipModel();
		List<TransferMeasureOwnerShipModel.Result> detailList = new ArrayList<TransferMeasureOwnerShipModel.Result>();
		for (User user : searchResults) {
			TransferMeasureOwnerShipModel.Result r = new TransferMeasureOwnerShipModel.Result();
			r.setFirstName(user.getFirstName());
			r.setLastName(user.getLastName());
			r.setEmailId(user.getEmailAddress());
			r.setKey(user.getId());
			detailList.add(r);
		}
		result.setData(detailList);
		result.setStartIndex(startIndex);
		result.setResultsTotal(getUserService().countSearchResultsNonAdmin(""));
		
		return result;
		
	}
	
	/**
	 * Setting Additional Attributes for Measure Xml.
	 * 
	 * @param measureDetailModel
	 *            - {@link ManageMeasureDetailModel}.
	 * @param measure
	 *            - {@link Measure}.
	 */
	private void setAdditionalAttrsForMeasureXml(final ManageMeasureDetailModel measureDetailModel, final Measure measure) {
		logger.info("In MeasureLibraryServiceImpl.setAdditionalAttrsForMeasureXml()");
		measureDetailModel.setId(measure.getId());
		measureDetailModel.setMeasureSetId(measure.getMeasureSet() != null ? measure.getMeasureSet().getId() : null);
		measureDetailModel.setOrgVersionNumber(MeasureUtility.formatVersionText(
				measureDetailModel.getRevisionNumber(), String.valueOf(measure.getVersionNumber())));
		measureDetailModel.setVersionNumber(MeasureUtility.getVersionText(measureDetailModel.getOrgVersionNumber(),
				measureDetailModel.getRevisionNumber(), measure.isDraft()));
		measureDetailModel.setId(UuidUtility.idToUuid(measureDetailModel.getId())); // have
		// to
		// change
		// on
		// unmarshalling.
		/*if (StringUtils.isNotBlank(measureDetailModel.getMeasFromPeriod())
				|| StringUtils.isNotBlank(measureDetailModel.getMeasToPeriod())) {*/
			PeriodModel periodModel = new PeriodModel();
			periodModel.setUuid(UUID.randomUUID().toString());
			//for New measures checking Calender year to add Default Dates
			if(!isMeasureCreated()){
				measureDetailModel.setCalenderYear(true);
			}
			periodModel.setCalenderYear(measureDetailModel.isCalenderYear());
			if(!measureDetailModel.isCalenderYear()){
				periodModel.setStartDate(measureDetailModel.getMeasFromPeriod());
				periodModel.setStopDate(measureDetailModel.getMeasToPeriod());
			} else { // for Default Dates
				periodModel.setStartDate("01/01/20XX");
				periodModel.setStopDate("12/31/20XX");
			}
//			if (StringUtils.isNotBlank(measureDetailModel.getMeasFromPeriod())) {
//				periodModel.setStartDate(measureDetailModel.getMeasFromPeriod());
//				//commented UUID as part of MAT-4613
//				//periodModel.setStartDateUuid(UUID.randomUUID().toString());
//			}
//			if (StringUtils.isNotBlank(measureDetailModel.getMeasToPeriod())) {
//				periodModel.setStopDate(measureDetailModel.getMeasToPeriod());
//				//commented UUID as part of MAT-4613
//				//periodModel.setStopDateUuid(UUID.randomUUID().toString());
//			}
			measureDetailModel.setPeriodModel(periodModel);
		//}
		
		if (StringUtils.isNotBlank(measureDetailModel.getGroupName())) {
			measureDetailModel.setQltyMeasureSetUuid(UUID.randomUUID().toString());
		}
		//MAT-4898
		//setOrgIdInAuthor(measureDetailModel.getAuthorSelectedList());
		setMeasureTypeAbbreviation(measureDetailModel.getMeasureTypeSelectedList());
		measureDetailModel.setScoringAbbr(setScoringAbbreviation(measureDetailModel.getMeasScoring()));
		
		if ((measureDetailModel.getEndorseByNQF() != null) && measureDetailModel.getEndorseByNQF()) {
			measureDetailModel.setEndorsement("National Quality Forum");
			measureDetailModel.setEndorsementId("2.16.840.1.113883.3.560");
		} else {
			measureDetailModel.setEndorsement(null);
			measureDetailModel.setEndorsementId(null);
		}
		NqfModel nqfModel = new NqfModel();
		nqfModel.setExtension(measureDetailModel.getNqfId());
		nqfModel.setRoot("2.16.840.1.113883.3.560.1");
		measureDetailModel.setNqfModel(nqfModel);
		if (CollectionUtils.isEmpty(MeasureDetailsUtil.getTrimmedList(measureDetailModel.getReferencesList()))) {
			measureDetailModel.setReferencesList(null);
		}
		logger.info("Exiting MeasureLibraryServiceImpl.setAdditionalAttrsForMeasureXml()..");
	}


	/**
	 * Sets the context.
	 * 
	 * @param context
	 *            the new context
	 */
	public void setContext(ApplicationContext context) {
		this.context = context;
	}
	
	/**
	 * Sets the dt oto model.
	 * 
	 * @param detailModelList
	 *            - {@link Result}.
	 * @param dto
	 *            - {@link MeasureShareDTO}.
	 * @param currentUserId
	 *            - {@link String}.
	 * @param isSuperUser
	 *            - {@link Boolean}. *
	 */
	private void setDTOtoModel(final List<ManageMeasureSearchModel.Result> detailModelList, final MeasureShareDTO dto,
			final String currentUserId, final boolean isSuperUser) {
		boolean isOwner = currentUserId.equals(dto.getOwnerUserId());
		ManageMeasureSearchModel.Result detail = new ManageMeasureSearchModel.Result();
		detail.setName(dto.getMeasureName());
		detail.setShortName(dto.getShortName());
		detail.setStatus(dto.getStatus());
		detail.setId(dto.getMeasureId());
		detail.setStatus(dto.getStatus());
		detail.setClonable(isOwner || isSuperUser);
		detail.setEditable((isOwner || isSuperUser || ShareLevel.MODIFY_ID.equals(dto.getShareLevel())) && dto.isDraft());
		detail.setMeasureLocked(dto.isLocked());
		detail.setExportable(dto.isPackaged());
		detail.setSharable(isOwner || isSuperUser);
		detail.setLockedUserInfo(dto.getLockedUserInfo());
		detail.setDraft(dto.isDraft());
		String formattedVersion = MeasureUtility.getVersionTextWithRevisionNumber(dto.getVersion(), dto.getRevisionNumber(), dto.isDraft());
		detail.setVersion(formattedVersion);
		detail.setScoringType(dto.getScoringType());
		detail.setMeasureSetId(dto.getMeasureSetId());
		detailModelList.add(detail);
	}
	
	/**
	 * Sets the measure package service.
	 * 
	 * @param measurePackagerService
	 *            the new measure package service
	 */
	public final void setMeasurePackageService(final MeasurePackageService measurePackagerService) {
		measurePackageService = measurePackagerService;
	}
	
	/**
	 * Sets the measure type abbreviation.
	 * 
	 * @param measureTypeList
	 *            the new measure type abbreviation
	 */
	private void setMeasureTypeAbbreviation(final List<MeasureType> measureTypeList) {
		if (measureTypeList != null) {
			for (MeasureType measureType : measureTypeList) {
				measureType.setAbbrDesc(MeasureDetailsUtil.getMeasureTypeAbbr(measureType.getDescription()));
			}
		}
	}
	
	/**
	 * Sets the org id in author.
	 * 
	 * @param authors
	 *            the new org id in author
	 */
	private void setOrgIdInAuthor(final List<Author> authors) {
		if (CollectionUtils.isNotEmpty(authors)) {
			for (Author author : authors) {
				String oid = getService().retrieveStewardOID(author.getAuthorName().trim());
				author.setOrgId((oid != null) && !oid.equals("") ? oid : UUID.randomUUID().toString());
			}
		}
	}
	
	/**
	 * Sets the scoring abbreviation.
	 * 
	 * @param measScoring
	 *            the meas scoring
	 * @return the string
	 */
	private String setScoringAbbreviation(final String measScoring) {
		return MeasureDetailsUtil.getScoringAbbr(measScoring);
	}
	
	/**
	 * Sets the user service.
	 * 
	 * @param usersService
	 *            the new user service
	 */
	public final void setUserService(final UserService usersService) {
		userService = usersService;
	}
	
	/**
	 * Sets the value from model.
	 * 
	 * @param model
	 *            the model
	 * @param measure
	 *            the measure
	 */
	private void setValueFromModel(final ManageMeasureDetailModel model, final Measure measure) {
		measure.setDescription(model.getName());
		measure.setaBBRName(model.getShortName());
		// US 421. Scoring choice is not part of core measure.
		measure.setMeasureScoring(model.getMeasScoring());
		measure.setVersion(model.getVersionNumber());
		measure.setDraft(model.isDraft());
		measure.setRevisionNumber(model.getRevisionNumber());
		/*measure.setMeasureStatus(model.getMeasureStatus());*/
		measure.seteMeasureId(model.geteMeasureId());
		if ((model.getFinalizedDate() != null) && !model.getFinalizedDate().equals("")) {
			measure.setFinalizedDate(new Timestamp(DateUtility.convertStringToDate(model.getFinalizedDate()).getTime()));
		}
		if ((model.getValueSetDate() != null) && !model.getValueSetDate().equals("")) {
			measure.setValueSetDate(new Timestamp(DateUtility.convertStringToDate(model.getValueSetDate()).getTime()));
		}
	}
	
	/** This method updates MeasureXML - Attributes Nodes
	 * 
	 * *.
	 * 
	 * @param list the list
	 * @param toEmail the to email */
	
	/*
	 * private void updateAttributes(final XmlProcessor processor, final
	 * QualityDataSetDTO modifyWithDTO, final QualityDataSetDTO modifyDTO) {
	 * 
	 * logger.debug(" MeasureLibraryServiceImpl: updateAttributes Start :  ");
	 * 
	 * String XPATH_EXPRESSION_ATTRIBUTE =
	 * "/measure//clause//attribute[@qdmUUID='"
	 * +modifyDTO.getUuid()+"']";//XPath to find all elementRefs in
	 * supplementalDataElements for to be modified QDM.
	 * 
	 * try { NodeList nodesATTR = (NodeList)
	 * xPath.evaluate(XPATH_EXPRESSION_ATTRIBUTE, processor.getOriginalDoc(),
	 * XPathConstants.NODESET); for(int i=0 ;i<nodesATTR.getLength();i++){ Node
	 * newNode = nodesATTR.item(i); newNode
	 * .getAttributes().getNamedItem("name").setNodeValue(modifyWithDTO
	 * .getDataType()); }
	 * 
	 * } catch (XPathExpressionException e) { e.printStackTrace(); }
	 * 
	 * logger.debug(" MeasureLibraryServiceImpl: updateAttributes End : "); }
	 */
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#transferOwnerShipToUser(java.util.List, java.lang.String)
	 */
	@Override
	public final void transferOwnerShipToUser(final List<String> list, final String toEmail) {
		MeasurePackageService service = getService();
		service.transferMeasureOwnerShipToUser(list, toEmail);
	}
	
	/**
	 * This method updates MeasureXML - QDM nodes under ElementLookUp.
	 * 
	 * *
	 * 
	 * @param processor
	 *            the processor
	 * @param modifyWithDTO
	 *            the modify with dto
	 * @param modifyDTO
	 *            the modify dto
	 */
	private void updateElementLookUp(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updateElementLookUp Start :  ");
		// XPath Expression to find all elementRefs in elementLookUp for to be modified QDM.
		String XPATH_EXPRESSION_ELEMENTLOOKUP = "/measure/elementLookUp/qdm[@uuid='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesElementLookUp = (NodeList) xPath.evaluate(XPATH_EXPRESSION_ELEMENTLOOKUP, processor.getOriginalDoc(),
					XPathConstants.NODESET);
			for (int i = 0; i < nodesElementLookUp.getLength(); i++) {
				Node newNode = nodesElementLookUp.item(i);
				newNode.getAttributes().getNamedItem("name").setNodeValue(modifyWithDTO.getCodeListName());
				newNode.getAttributes().getNamedItem("id").setNodeValue(modifyWithDTO.getId());
				if ((newNode.getAttributes().getNamedItem("codeSystemName") == null)
						&& (modifyWithDTO.getCodeSystemName() != null)) {
					Attr attrNode = processor.getOriginalDoc().createAttribute("codeSystemName");
					attrNode.setNodeValue(modifyWithDTO.getCodeSystemName());
					newNode.getAttributes().setNamedItem(attrNode);
				} else if ((newNode.getAttributes().getNamedItem("codeSystemName") != null)
						&& (modifyWithDTO.getCodeSystemName() == null)) {
					newNode.getAttributes().getNamedItem("codeSystemName").setNodeValue(null);
				} else if ((newNode.getAttributes().getNamedItem("codeSystemName") != null)
						&& (modifyWithDTO.getCodeSystemName() != null)) {
					newNode.getAttributes().getNamedItem("codeSystemName").setNodeValue(
							modifyWithDTO.getCodeSystemName());
				}
				newNode.getAttributes().getNamedItem("datatype").setNodeValue(modifyWithDTO.getDataType());
				newNode.getAttributes().getNamedItem("oid").setNodeValue(modifyWithDTO.getOid());
				newNode.getAttributes().getNamedItem("taxonomy").setNodeValue(modifyWithDTO.getTaxonomy());
				newNode.getAttributes().getNamedItem("version").setNodeValue(modifyWithDTO.getVersion());
				if (modifyWithDTO.isSuppDataElement()) {
					newNode.getAttributes().getNamedItem("suppDataElement").setNodeValue("true");
				} else {
					newNode.getAttributes().getNamedItem("suppDataElement").setNodeValue("false");
				}
				if (newNode.getAttributes().getNamedItem("instance") != null) {
					if (!StringUtils.isBlank(modifyWithDTO.getOccurrenceText())) {
						newNode.getAttributes().getNamedItem("instance").setNodeValue(
								modifyWithDTO.getOccurrenceText());
					} else {
						newNode.getAttributes().removeNamedItem("instance");
					}
				} else {
					if (!StringUtils.isEmpty(modifyWithDTO.getOccurrenceText())) {
						Attr instance = processor.getOriginalDoc().createAttribute("instance");
						instance.setNodeValue(modifyWithDTO.getOccurrenceText());
						newNode.getAttributes().setNamedItem(instance);
					}
				}
				if (newNode.getAttributes().getNamedItem("effectiveDate") != null) {
					if (!StringUtils.isBlank(modifyWithDTO.getEffectiveDate())) {
						newNode.getAttributes().getNamedItem("effectiveDate").setNodeValue(
								modifyWithDTO.getEffectiveDate());
					} else {
						newNode.getAttributes().removeNamedItem("effectiveDate");
					}
				} else {
					if (!StringUtils.isEmpty(modifyWithDTO.getEffectiveDate())) {
						Attr effectiveDateAttr = processor.getOriginalDoc().createAttribute("effectiveDate");
						effectiveDateAttr.setNodeValue(modifyWithDTO.getEffectiveDate());
						newNode.getAttributes().setNamedItem(effectiveDateAttr);
					}
				}
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		logger.debug(" MeasureLibraryServiceImpl: updateElementLookUp End :  ");
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see mat.client.measure.service.MeasureService#updateLockedDate
	 * (java.lang.String, java.lang.String).This method has been added to update
	 * the measureLock Date. This method first gets the exisitingMeasure and
	 * then adds the lockedOutDate if it is not there.
	 */
	@Override
	public final SaveMeasureResult updateLockedDate(final String measureId, final String userId) {
		Measure existingmeasure = null;
		User user = null;
		SaveMeasureResult result = new SaveMeasureResult();
		if ((measureId != null) && (userId != null)) {
			existingmeasure = getService().getById(measureId);
			if (existingmeasure != null) {
				if (!isLocked(existingmeasure)) {
					user = getUserService().getById(userId);
					existingmeasure.setLockedUser(user);
					existingmeasure.setLockedOutDate(new Timestamp(new Date().getTime()));
					getService().save(existingmeasure);
					result.setSuccess(true);
				}
			}
		}
		
		result.setId(existingmeasure.getId());
		return result;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#updateMeasureNotes(mat.DTO.MeasureNoteDTO, java.lang.String)
	 */
	@Override
	public final void updateMeasureNotes(final MeasureNoteDTO measureNoteDTO, final String userId) {
		try {
			MeasureNotesDAO measureNotesDAO = getMeasureNotesDAO();
			MeasureNotes measureNotes = measureNotesDAO.find(measureNoteDTO.getId());
			measureNotes.setNoteTitle(measureNoteDTO.getNoteTitle());
			measureNotes.setNoteDesc(measureNoteDTO.getNoteDesc());
			User user = getUserService().getById(userId);
			if (user != null) {
				measureNotes.setModifyUser(user);
			}
			measureNotes.setLastModifiedDate(new Date());
			getMeasureNotesService().saveMeasureNote(measureNotes);
			logger.info("Edited MeasureNotes Saved Successfully. Measure notes Id :: " + measureNoteDTO.getId());
		} catch (Exception e) {
			logger.info("Edited MeasureNotes not saved. Exception occured. Measure notes Id :: " + measureNoteDTO.getId());
		}
	}
	
	/**
	 * This method updates MeasureXML - ElementLookUpNode,ElementRef's under
	 * Population Node and Stratification Node, SupplementDataElements. It also
	 * removes attributes nodes if there is mismatch in data types of newly
	 * selected QDM and already applied QDM.
	 * 
	 * *
	 * 
	 * @param modifyWithDTO
	 *            the modify with dto
	 * @param modifyDTO
	 *            the modify dto
	 * @param measureId
	 *            the measure id
	 */
	@Override
	public final void updateMeasureXML(final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO, final String measureId) {
		logger.debug(" MeasureLibraryServiceImpl: updateMeasureXML Start : Measure Id :: " + measureId);
		MeasureXmlModel model = getMeasureXmlForMeasure(measureId);
		
		if (model != null) {
			XmlProcessor processor = new XmlProcessor(model.getXml());
			if (modifyDTO.isUsed()) {
				if (modifyDTO.getDataType().equalsIgnoreCase("Attribute")) {
					// update All Attributes.
					// updateAttributes(processor, modifyWithDTO, modifyDTO);
				} else {
					// Update all elementRef's in Populations and Stratification
					//updatePopulationAndStratification(processor, modifyWithDTO, modifyDTO);
				}
				
				//Update all elementRef's in SubTreeLookUp
				updateSubTreeLookUp(processor, modifyWithDTO, modifyDTO);
				
				//Update all elementRef's in ItemCount
				updateItemCount(processor, modifyWithDTO, modifyDTO);
				
				//Update all elementsRefs in Package Clauses
				updatePackageClauseItemCount(processor, modifyWithDTO, modifyDTO);
				
				// update elementLookUp Tag
				updateElementLookUp(processor, modifyWithDTO, modifyDTO);
				updateSupplementalDataElement(processor, modifyWithDTO, modifyDTO);
				model.setXml(processor.transform(processor.getOriginalDoc()));
				getService().saveMeasureXml(model);
				
			} else {
				// update elementLookUp Tag
				updateElementLookUp(processor, modifyWithDTO, modifyDTO);
				model.setXml(processor.transform(processor.getOriginalDoc()));
				getService().saveMeasureXml(model);
			}
			
		}
		logger.debug(" MeasureLibraryServiceImpl: updateMeasureXML End : Measure Id :: " + measureId);
	}
	
	/**
	 * Update sub tree look up.
	 *
	 * @param processor the processor
	 * @param modifyWithDTO the modify with dto
	 * @param modifyDTO the modify dto
	 */
	private void updateSubTreeLookUp(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updateSubTreeLookUp Start :  ");
		// XPath to find All elementRef's under subTreeLookUp element nodes for to be modified QDM.
		String XPATH_EXPRESSION_SubTreeLookUp_ELEMENTREF = "/measure//subTreeLookUp//elementRef[@id='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesClauseWorkSpace = (NodeList) xPath.evaluate(XPATH_EXPRESSION_SubTreeLookUp_ELEMENTREF,
					processor.getOriginalDoc(),	XPathConstants.NODESET);
			for (int i = 0; i < nodesClauseWorkSpace.getLength(); i++) {
				Node newNode = nodesClauseWorkSpace.item(i);
				String displayName = new String();
				if (!StringUtils.isBlank(modifyWithDTO.getOccurrenceText())) {
					displayName = displayName.concat(modifyWithDTO.getOccurrenceText() + " of ");
				}
				displayName = displayName.concat(modifyWithDTO.getCodeListName() + " : " + modifyWithDTO.getDataType());
				
				newNode.getAttributes().getNamedItem("displayName").setNodeValue(displayName);
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			
		}
		logger.debug(" MeasureLibraryServiceImpl: updateSubTreeLookUp End :  ");
	}
	
	/**
	 * Update item count.
	 *
	 * @param processor the processor
	 * @param modifyWithDTO the modify with dto
	 * @param modifyDTO the modify dto
	 */
	private void updateItemCount(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updateItemCount Start :  ");
		// XPath to find All elementRef's under itemCount element nodes for to be modified QDM.
		String XPATH_EXPRESSION_ItemCount_ELEMENTREF = "/measure//measureDetails//itemCount//elementRef[@id='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesItemCount = (NodeList) xPath.evaluate(XPATH_EXPRESSION_ItemCount_ELEMENTREF,
					processor.getOriginalDoc(),	XPathConstants.NODESET);
			for (int i = 0; i < nodesItemCount.getLength(); i++) {
				Node newNode = nodesItemCount.item(i);
				String instance = new String();
				String name = new String();
				String dataType = new String();
				String oid = new String();
				if (!StringUtils.isBlank(modifyWithDTO.getOccurrenceText())) {
					instance = instance.concat(modifyWithDTO.getOccurrenceText() + " of ");
					newNode.getAttributes().getNamedItem("instance").setNodeValue(instance);
				}
				name = modifyWithDTO.getCodeListName();
				dataType = modifyWithDTO.getDataType();
				oid = modifyWithDTO.getOid();
				newNode.getAttributes().getNamedItem("name").setNodeValue(name);
				newNode.getAttributes().getNamedItem("dataType").setNodeValue(dataType);
				newNode.getAttributes().getNamedItem("oid").setNodeValue(oid);
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			
		}
		logger.debug(" MeasureLibraryServiceImpl: updateItemCount End :  ");
	}
	
	
	
	
	/**
	 * This method updates MeasureXML - ElementRef's under Population and
	 * Stratification Node
	 * 
	 * *.
	 * 
	 * @param processor
	 *            the processor
	 * @param modifyWithDTO
	 *            the modify with dto
	 * @param modifyDTO
	 *            the modify dto
	 */
	private void updatePopulationAndStratification(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updatePopulationAndStratification Start :  ");
		// XPath to find All elementRef's under clause element nodes for to be modified QDM.
		String XPATH_EXPRESSION_CLAUSE_ELEMENTREF = "/measure//subTreeLookUp//elementRef[@id='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesClauseWorkSpace = (NodeList) xPath.evaluate(XPATH_EXPRESSION_CLAUSE_ELEMENTREF,
					processor.getOriginalDoc(),	XPathConstants.NODESET);
			ArrayList<QDSAttributes> attr = (ArrayList<QDSAttributes>) getAllDataTypeAttributes(modifyWithDTO.getDataType());
			for (int i = 0; i < nodesClauseWorkSpace.getLength(); i++) {
				Node newNode = nodesClauseWorkSpace.item(i);
				String displayName = new String();
				if (!StringUtils.isBlank(modifyWithDTO.getOccurrenceText())) {
					displayName = displayName.concat(modifyWithDTO.getOccurrenceText() + " of ");
				}
				displayName = displayName.concat(modifyWithDTO.getCodeListName() + " : " + modifyWithDTO.getDataType());
				
				newNode.getAttributes().getNamedItem("displayName").setNodeValue(displayName);
				if (newNode.getChildNodes() != null) {
					NodeList childList = newNode.getChildNodes();
					for (int j = 0; j < childList.getLength(); j++) {
						Node childNode = childList.item(j);
						if (childNode.getAttributes().getNamedItem("qdmUUID") != null) {
							String childNodeAttrName = childNode.getAttributes().getNamedItem("name").
									getNodeValue();
							boolean isRemovable = true;
							for (QDSAttributes attributes : attr) {
								if (attributes.getName().equalsIgnoreCase(childNodeAttrName)) {
									isRemovable = false;
									break;
								}
							}
							if (isRemovable) {
								Node parentNode = childNode.getParentNode();
								parentNode.removeChild(childNode);
							}
						}
					}
				}
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			
		}
		logger.debug(" MeasureLibraryServiceImpl: updatePopulationAndStratification End :  ");
	}
	
	/**
	 * Update package clause item count.
	 *
	 * @param processor the processor
	 * @param modifyWithDTO the modify with dto
	 * @param modifyDTO the modify dto
	 */
	private void updatePackageClauseItemCount(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updatePackageClauseItemCount Start :  ");
		// XPath to find All elementRef's under itemCount element nodes for to be modified QDM.
		String XPATH_EXPRESSION_ItemCount_ELEMENTREF = "/measure//measureGrouping//packageClause//elementRef[@id='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesItemCount = (NodeList) xPath.evaluate(XPATH_EXPRESSION_ItemCount_ELEMENTREF,
					processor.getOriginalDoc(),	XPathConstants.NODESET);
			for (int i = 0; i < nodesItemCount.getLength(); i++) {
				Node newNode = nodesItemCount.item(i);
				String instance = new String();
				String name = new String();
				String dataType = new String();
				String oid = new String();
				if (!StringUtils.isBlank(modifyWithDTO.getOccurrenceText())) {
					instance = instance.concat(modifyWithDTO.getOccurrenceText() + " of ");
					newNode.getAttributes().getNamedItem("instance").setNodeValue(instance);
				}
				name = modifyWithDTO.getCodeListName();
				dataType = modifyWithDTO.getDataType();
				oid = modifyWithDTO.getOid();
				newNode.getAttributes().getNamedItem("name").setNodeValue(name);
				newNode.getAttributes().getNamedItem("dataType").setNodeValue(dataType);
				newNode.getAttributes().getNamedItem("oid").setNodeValue(oid);
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			
		}
		logger.debug(" MeasureLibraryServiceImpl: updatePackageClauseItemCount End :  ");
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#updatePrivateColumnInMeasure(java.lang.String, boolean)
	 */
	@Override
	public final void updatePrivateColumnInMeasure(final String measureId, final boolean isPrivate) {
		getService().updatePrivateColumnInMeasure(measureId, isPrivate);
	}
	
	/** This method updates MeasureXML - ElementRef's under SupplementalDataElement Node.
	 * 
	 * @param processor the processor
	 * @param modifyWithDTO QualityDataSetDTO
	 * @param modifyDTO QualityDataSetDTO */
	private void updateSupplementalDataElement(final XmlProcessor processor, final QualityDataSetDTO modifyWithDTO,
			final QualityDataSetDTO modifyDTO) {
		
		logger.debug(" MeasureLibraryServiceImpl: updateSupplementalDataElement Start :  ");
		// XPath to find All elementRef's in supplementalDataElements for to be modified QDM.
		String XPATH_EXPRESSION_SDE_ELEMENTREF = "/measure/supplementalDataElements/elementRef[@id='"
				+ modifyDTO.getUuid() + "']";
		try {
			NodeList nodesSDE = (NodeList) xPath.evaluate(XPATH_EXPRESSION_SDE_ELEMENTREF, processor.getOriginalDoc(),
					XPathConstants.NODESET);
			for (int i = 0; i < nodesSDE.getLength(); i++) {
				Node newNode = nodesSDE.item(i);
				newNode.getAttributes().getNamedItem("name").setNodeValue(modifyWithDTO.getCodeListName());
			}
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		logger.debug(" MeasureLibraryServiceImpl: updateSupplementalDataElement End :  ");
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#updateUsersShare(mat.client.measure.ManageMeasureShareModel)
	 */
	@Override
	public final void updateUsersShare(final ManageMeasureShareModel model) {
		getService().updateUsersShare(model);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see mat.server.service.MeasureLibraryService#validateMeasureForExport(java.lang.String, java.util.ArrayList)
	 */
	@Override
	public final ValidateMeasureResult validateMeasureForExport(final String key, final List<MatValueSet> matValueSetList)
			throws MatException {
		try {
			return getService().validateMeasureForExport(key, matValueSetList);
		} catch (Exception exc) {
			logger.info("Exception validating export for " + key, exc);
			throw new MatException(exc.getMessage());
		}
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getFormattedReleaseDate(java.lang.String)
	 */
	@Override
	public Date getFormattedReleaseDate(String releaseDate){
		
		SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");
		Date date = new Date();
		try {
			date = formatter.parse(releaseDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return date;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getHumanReadableForNode(java.lang.String, java.lang.String)
	 */
	@Override
	public String getHumanReadableForNode(final String measureId, final String populationSubXML){
		String humanReadableHTML = "";
		try {
			humanReadableHTML = getService().getHumanReadableForNode(measureId, populationSubXML);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return humanReadableHTML;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getComponentMeasures(java.util.List)
	 */
	@Override
	public ManageMeasureSearchModel getComponentMeasures(List<String> measureIds) {
		ManageMeasureSearchModel searchModel = new ManageMeasureSearchModel();
		List<Measure> measureList = getService().getComponentMeasuresInfo(measureIds);
		List<ManageMeasureSearchModel.Result> detailModelList = new ArrayList<ManageMeasureSearchModel.Result>();
		searchModel.setData(detailModelList);
		for (Measure measure : measureList) {
			ManageMeasureSearchModel.Result detail = extractManageMeasureSearchModelDetail(measure);
			detailModelList.add(detail);
		}
		return searchModel;
	}
	
	/**
	 * Extract manage measure search model detail.
	 *
	 * @param measure the measure
	 * @return the manage measure search model. result
	 */
	private ManageMeasureSearchModel.Result extractManageMeasureSearchModelDetail(Measure measure){
		ManageMeasureSearchModel.Result detail = new ManageMeasureSearchModel.Result();
		detail.setName(measure.getDescription());
		detail.setId(measure.getId());
		String formattedVersion = MeasureUtility.getVersionTextWithRevisionNumber(measure.getVersion(), measure.getRevisionNumber(), measure.isDraft());
		detail.setVersion(formattedVersion);
		detail.setFinalizedDate(measure.getFinalizedDate());
		return detail;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#validatePackageGrouping(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public boolean validatePackageGrouping(ManageMeasureDetailModel model) {
		boolean flag=false;
		
		logger.debug(" MeasureLibraryServiceImpl: validatePackageGrouping Start :  ");
		
		
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(model.getId());
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			/*System.out.println("MEASURE_XML: "+xmlModel.getXml());*/
			flag = validateMeasureXmlAtCreateMeasurePackager(xmlModel);
		}
		
		return flag;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#validateMeasureXmlInpopulationWorkspace(mat.client.clause.clauseworkspace.model.MeasureXmlModel)
	 */
	@Override
	public boolean validateMeasureXmlAtCreateMeasurePackager(MeasureXmlModel measureXmlModel) {
		boolean flag=false;
		
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(measureXmlModel.getMeasureId());
		
		if ((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml())) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			
			//validate only from MeasureGrouping
			String XAPTH_MEASURE_GROUPING="/measure/measureGrouping/ group/packageClause" +
					"[not(@uuid = preceding:: group/packageClause/@uuid)]";
			
			List<String> measureGroupingIDList = new ArrayList<String>();;
			
			try {
				NodeList measureGroupingNodeList = (NodeList) xPath.evaluate(XAPTH_MEASURE_GROUPING, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				
				for(int i=0 ; i<measureGroupingNodeList.getLength();i++){
					Node childNode = measureGroupingNodeList.item(i);
					String uuid = childNode.getAttributes().getNamedItem("uuid").getNodeValue();
					String type = childNode.getAttributes().getNamedItem("type").getNodeValue();
					if(type.equals("stratification")){
						List<String> stratificationClausesIDlist = getStratificationClasuesIDList(uuid, xmlProcessor);
						measureGroupingIDList.addAll(stratificationClausesIDlist);
					} else {
						measureGroupingIDList.add(uuid);
					}
				}
			} catch (XPathExpressionException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			
			String uuidXPathString = "";
			for (String uuidString: measureGroupingIDList) {
				uuidXPathString += "@uuid = '" + uuidString + "' or";
			}
			
			uuidXPathString = uuidXPathString.substring(0, uuidXPathString.lastIndexOf(" or"));
			
			//String XPATH_POPULATION = "/measure//clause["+uuidXPathString+"]";
			
			String XPATH_POPULATION_LOGICALOP = "/measure//clause["+uuidXPathString+"]//logicalOp";
			
			String XPATH_POPULATION_QDMELEMENT = "/measure//clause["+uuidXPathString+"]//elementRef";
			
			String XPATH_POPULATION_TIMING_ELEMENT = "/measure//clause["+uuidXPathString+"]//relationalOp";
			
			String XPATH_POPULATION_FUNCTIONS ="/measure//clause["+uuidXPathString+"]//functionalOp";
			
			
			
			//get the Population Worspace Logic that are Used in Measure Grouping
			
			try {
				//list of LogicalOpNode inSide the PopulationWorkspace That are used in Grouping
				NodeList populationLogicalOp = (NodeList) xPath.evaluate(XPATH_POPULATION_LOGICALOP, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				//list of Qdemelement inSide the PopulationWorkspace That are used in Grouping
				NodeList populationQdemElement = (NodeList) xPath.evaluate(XPATH_POPULATION_QDMELEMENT, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				//list of TimingElement inSide the PopulationWorkspace That are used in Grouping
				NodeList populationTimingElement = (NodeList) xPath.evaluate(XPATH_POPULATION_TIMING_ELEMENT, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				//list of functionNode inSide the PopulationWorkspace That are used in Grouping
				NodeList populationFunctions = (NodeList) xPath.evaluate(XPATH_POPULATION_FUNCTIONS, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				
				if(populationLogicalOp.getLength()>0){
					for (int i = 0; (i <populationLogicalOp.getLength()) && !flag; i++) {
						Node childNode =populationLogicalOp.item(i);
						String type = childNode.getParentNode().getAttributes().getNamedItem("type").getNodeValue();
						if(type.equals("measureObservation")){
							flag = true;
							break;
						}
					}
				}
				
				if((populationQdemElement.getLength()>0) && !flag){
					flag = true;
				}
				
				if((populationTimingElement.getLength()>0) && !flag){
					flag = true;
				}
				if((populationFunctions.getLength()>0) && !flag){
					flag = true;
				}
				
				
			} catch (XPathExpressionException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			//start clause validation
			Map<String , List<String>> usedSubtreeRefIdsMap = getUsedSubtreeRefIds(xmlProcessor,measureGroupingIDList);
			//List<String> usedSubTreeIds = checkUnUsedSubTreeRef(xmlProcessor, usedSubtreeRefIds);
			List<String> usedSubTreeIds = checkUnUsedSubTreeRef(xmlProcessor, usedSubtreeRefIdsMap);
			//to get all Operators for validaiton during Package timing for Removed Operators
			List<String> operatorTypeList = getAllOperatorsTypeList();
			
			if(usedSubTreeIds.size()>0){
				
				for(String usedSubtreeRefId:usedSubTreeIds){
					
					String satisfyFunction = "@type='SATISFIES ALL' or @type='SATISFIES ANY'";
					String otherThanSatisfyfunction = "@type!='SATISFIES ALL' or @type!='SATISFIES ANY'";
					String dateTimeDiffFunction = "@type='DATETIMEDIFF'";
					//geting list of IDs
					String XPATH_QDMELEMENT = "/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//elementRef/@id";
					//geting Unique Ids only
					//String XPATH_QDMELEMENT = "/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//elementRef[not(@id =  preceding:: elementRef/@id)]";
					String XPATH_TIMING_ELEMENT = "/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//relationalOp";
					String XPATH_SATISFY_ELEMENT = "/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//functionalOp["+satisfyFunction+"]";
					String XPATH_FUNCTIONS ="/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//functionalOp["+otherThanSatisfyfunction+"]";
					String XPATH_SETOPERATOR ="/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//setOp";
					//for DateTimeDiff Validation
					String XPATH_DATE_TIME_DIFF_ELEMENT = "/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//functionalOp["+dateTimeDiffFunction+"]";
					/*System.out.println("MEASURE_XML: "+xmlModel.getXml());*/
					try {
						
						
						NodeList nodesSDE_qdmElementId = (NodeList) xPath.evaluate(XPATH_QDMELEMENT, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						NodeList nodesSDE_timingElement = (NodeList) xPath.evaluate(XPATH_TIMING_ELEMENT, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						NodeList nodesSDE_satisfyElement = (NodeList) xPath.evaluate(XPATH_SATISFY_ELEMENT, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						NodeList nodesSDE_functions = (NodeList) xPath.evaluate(XPATH_FUNCTIONS, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						NodeList nodeSDE_setoperator =(NodeList) xPath.evaluate(XPATH_SETOPERATOR, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						NodeList nodeSDE_dateTimeDiffElement =(NodeList) xPath.evaluate(XPATH_DATE_TIME_DIFF_ELEMENT, xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						
						for (int n = 0; (n <nodesSDE_timingElement.getLength()) && !flag; n++) {
							
							Node timingElementchildNode =nodesSDE_timingElement.item(n);
							flag = validateTimingRelationshipNode(timingElementchildNode, operatorTypeList, flag);
							if(flag) {
								break;
							}
							
						}
						for (int j = 0; (j <nodesSDE_satisfyElement.getLength()) && !flag; j++) {
							
							Node satisfyElementchildNode =nodesSDE_satisfyElement.item(j);
							flag = validateSatisfyNode(satisfyElementchildNode, flag);
							if(flag) {
								break;
							}
							
						}
						
						for (int m = 0; (m <nodesSDE_qdmElementId.getLength()) && !flag; m++) {	
							String id = nodesSDE_qdmElementId.item(m).getNodeValue();
							String xpathForQdmWithAttributeList ="/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//elementRef[@id='"+id+"']/attribute";
							String xpathForQdmWithOutAttributeList ="/measure//subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']//elementRef[@id='"+id+"'][not(attribute)]";
							String XPATH_QDMLOOKUP = "/measure/elementLookUp/qdm[@uuid='"+id+"']";
							Node qdmNode = (Node)xPath.evaluate(XPATH_QDMLOOKUP, xmlProcessor.getOriginalDoc(),XPathConstants.NODE);
							NodeList qdmWithAttributeNodeList = (NodeList)xPath.evaluate(xpathForQdmWithAttributeList, xmlProcessor.getOriginalDoc(),XPathConstants.NODESET);
							NodeList qdmWithOutAttributeList = (NodeList)xPath.evaluate(xpathForQdmWithOutAttributeList, xmlProcessor.getOriginalDoc(),XPathConstants.NODESET);														
							//validation for QDMwithAttributeList
							//checking for all the Attribute That are used for The Id
							for(int n=0; n<qdmWithAttributeNodeList.getLength(); n++){								
									String attributeName = qdmWithAttributeNodeList.item(n).getAttributes().getNamedItem("name").getNodeValue();								
								flag = !validateQdmNode(qdmNode, attributeName);							
								if(flag){
									break;
								}
							}
							//validation for QDMwithOutAttributeList for the Id							
							if(!flag && qdmWithOutAttributeList.getLength() >0){	
								String attributeName ="";
								flag = !validateQdmNode(qdmNode, attributeName);							
								if(flag){
									break;
								}
							}
							
						}
						
						for (int n = 0; (n <nodesSDE_functions.getLength()) && !flag; n++) {
							
							Node functionsChildNode =nodesSDE_functions.item(n);
							flag = validateFunctionNode(functionsChildNode, operatorTypeList, flag);
							if(flag) {
								break;
							}
							
						}
						
						for (int n = 0; (n <nodeSDE_setoperator.getLength()) && !flag; n++) {
							
							Node setOperatorChildNode =nodeSDE_setoperator.item(n);
							flag = validateSetOperatorNode(setOperatorChildNode, flag);
							if(flag) {
								break;
							}
							
						}
						
						for (int n = 0; (n < nodeSDE_dateTimeDiffElement.getLength())
								&& !flag; n++) {
							
							if(usedSubtreeRefIdsMap.get("subTreeIDAtPop").contains(usedSubtreeRefId) ||
									usedSubtreeRefIdsMap.get("subTreeIDAtStrat").contains(usedSubtreeRefId)){
								flag = true;
							}
							
							if (flag) {
								break;
							}
							
						}
						
					} catch (XPathExpressionException e) {
						
						e.printStackTrace();
					}
				}
			}
			
		}
		return flag;
	}
	
	
	/**
	 * Check if qdm var instance is present.
	 *
	 * @param usedSubtreeRefId the used subtree ref id
	 * @param xmlProcessor the xml processor
	 * @return the string
	 */
	private String checkIfQDMVarInstanceIsPresent(String usedSubtreeRefId,
			XmlProcessor xmlProcessor){
		
		String XPATH_INSTANCE_QDM_VAR = "/measure/subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']/@instance";
		String XPATH_INSTANCE_OF_QDM_VAR = "/measure/subTreeLookUp/subTree[@uuid='"+usedSubtreeRefId+"']/@instanceOf";
		try {
			Node nodesSDE_SubTree = (Node) xPath.evaluate(XPATH_INSTANCE_QDM_VAR, xmlProcessor.getOriginalDoc(),
					XPathConstants.NODE);
			if(nodesSDE_SubTree!=null){
				Node nodesSDE_SubTreeInstance = (Node) xPath.evaluate(XPATH_INSTANCE_OF_QDM_VAR, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODE);
				usedSubtreeRefId = nodesSDE_SubTreeInstance.getNodeValue();
			}
			
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return usedSubtreeRefId;
	}
	
	/**
	 * Gets the filtered sub tree ids.
	 *
	 * @param xmlProcessor the xml processor
	 * @param usedSubTreeIdsMap the used sub tree ids map
	 * @return the filtered sub tree ids
	 */
	private List<String> checkUnUsedSubTreeRef(
			XmlProcessor xmlProcessor, Map<String, List<String>> usedSubTreeIdsMap) {
		
		List<String> subTreeIdsAtPop = new ArrayList<String>();
		List<String> subTreeIdsAtMO = new ArrayList<String>();
		List<String> subTreeIdsAtStrat = new ArrayList<String>();
		subTreeIdsAtPop.addAll(usedSubTreeIdsMap.get("subTreeIDAtPop"));
		subTreeIdsAtMO.addAll(usedSubTreeIdsMap.get("subTreeIDAtMO"));
		subTreeIdsAtStrat.addAll(usedSubTreeIdsMap.get("subTreeIDAtStrat"));
		List<String> subTreeIdsAtRAV = getUsedRiskAdjustmentVariables(xmlProcessor);
		subTreeIdsAtPop.removeAll(subTreeIdsAtMO);
		subTreeIdsAtMO.addAll(subTreeIdsAtPop);
		
		subTreeIdsAtMO.removeAll(subTreeIdsAtStrat);
		subTreeIdsAtStrat.addAll(subTreeIdsAtMO);
		
		//to get Used SubTreeRef form Risk Adjustment Variables
		subTreeIdsAtStrat.removeAll(subTreeIdsAtRAV);
		subTreeIdsAtRAV.addAll(subTreeIdsAtStrat);
		
		return subTreeIdsAtRAV;
	}
	
	
	/**
	 * Gets the used risk adjustment variables.
	 *
	 * @param xmlProcessor the xml processor
	 * @return the used risk adjustment variables
	 */
	private List<String> getUsedRiskAdjustmentVariables(XmlProcessor xmlProcessor){
		List<String> subTreeRefRAVList = new ArrayList<String>();
		
		String xpathforRiskAdjustmentVariables = "/measure/riskAdjustmentVariables/subTreeRef";
		try {
			NodeList subTreeRefIdsNodeListRAV = (NodeList) xPath.evaluate(xpathforRiskAdjustmentVariables,
					xmlProcessor.getOriginalDoc(), XPathConstants.NODESET);
			for(int i=0;i<subTreeRefIdsNodeListRAV.getLength();i++){
				Node childNode = subTreeRefIdsNodeListRAV.item(i);
				subTreeRefRAVList.add(childNode.getAttributes().
						getNamedItem("id").getNodeValue());
			}
			
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return subTreeRefRAVList;
	}
	
	
	/**
	 * Gets the stratification clasues id list.
	 *
	 * @param uuid the uuid
	 * @param xmlProcessor the xml processor
	 * @return the stratification clasues id list
	 */
	private List<String> getStratificationClasuesIDList(String uuid, XmlProcessor xmlProcessor) {
		
		String XPATH_MEASURE_GROUPING_STRATIFICATION_CLAUSES = "/measure/strata/stratification" +
				"[@uuid='"+uuid+"']/clause/@uuid";
		List<String> clauseList = new ArrayList<String>();
		try {
			NodeList stratificationClausesNodeList = (NodeList)xPath.evaluate(XPATH_MEASURE_GROUPING_STRATIFICATION_CLAUSES,
					xmlProcessor.getOriginalDoc(),XPathConstants.NODESET);
			for(int i=0;i<stratificationClausesNodeList.getLength();i++){
				clauseList.add(stratificationClausesNodeList.item(i).getNodeValue());
			}
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return clauseList;
	}
	
	/**
	 * Gets the used subtree ref ids.
	 *
	 * @param xmlProcessor the xml processor
	 * @param measureGroupingIDList the measure grouping id list
	 * @return the used subtree ref ids
	 */
	private Map<String , List<String>> getUsedSubtreeRefIds(XmlProcessor xmlProcessor, List<String> measureGroupingIDList) {
		
		List<String> usedSubTreeRefIdsPop = new ArrayList<String>();
		List<String> usedSubTreeRefIdsStrat = new ArrayList<String>();
		List<String> usedSubTreeRefIdsMO = new ArrayList<String>();
		Map<String , List<String>> usedSubTreeIdsMap = new HashMap<String, List<String>>();
		NodeList groupedSubTreeRefIdsNodeListPop;
		NodeList groupedSubTreeRefIdsNodeListMO;
		NodeList groupedSubTreeRefIdListStrat;
		String uuidXPathString = "";
		
		for(String uuidString: measureGroupingIDList){
			uuidXPathString += "@uuid = '"+uuidString + "' or";
		}
		
		uuidXPathString = uuidXPathString.substring(0,uuidXPathString.lastIndexOf(" or"));
		String XPATH_POPULATION_SUBTREEREF = "/measure/populations//clause["+uuidXPathString+"]//subTreeRef[not(@id = preceding:: populations//clause//subTreeRef/@id)]/@id";
		
		try {
			// Populations, MeasureObervations and Startification
			groupedSubTreeRefIdsNodeListPop = (NodeList) xPath.evaluate(XPATH_POPULATION_SUBTREEREF,
					xmlProcessor.getOriginalDoc(), XPathConstants.NODESET);
			
			for (int i = 0; i < groupedSubTreeRefIdsNodeListPop.getLength(); i++) {
				Node groupedSubTreeRefIdAttributeNodePop = groupedSubTreeRefIdsNodeListPop
						.item(i);
				String uuid = groupedSubTreeRefIdAttributeNodePop
						.getNodeValue();
				
				uuid = checkIfQDMVarInstanceIsPresent(uuid, xmlProcessor);
				if(!usedSubTreeRefIdsPop.contains(uuid)){
					usedSubTreeRefIdsPop.add(uuid);
				}
			}
			
			//to get the Used SubtreeIds from Population Tab.
			List<String> usedSubtreeIdsAtPop = checkUnUsedSubTreeRef(xmlProcessor, usedSubTreeRefIdsPop);
			
			// Measure Observations
			String measureObservationSubTreeRefID = "/measure/measureObservations//clause["+
					uuidXPathString+"]//subTreeRef[not(@id = preceding:: measureObservations//clause//subTreeRef/@id)]/@id";
			groupedSubTreeRefIdsNodeListMO = (NodeList) xPath.evaluate(measureObservationSubTreeRefID,
					xmlProcessor.getOriginalDoc(), XPathConstants.NODESET);
			
			for (int i = 0; i < groupedSubTreeRefIdsNodeListMO.getLength(); i++) {
				Node groupedSubTreeRefIdAttributeNodeMO = groupedSubTreeRefIdsNodeListMO
						.item(i);
				String uuid = groupedSubTreeRefIdAttributeNodeMO.getNodeValue();
				uuid = checkIfQDMVarInstanceIsPresent(uuid, xmlProcessor);
				if(!usedSubTreeRefIdsMO.contains(uuid)){
					usedSubTreeRefIdsMO.add(uuid);
				}
			}
			
			//used SubtreeIds at Measure Observation
			List<String> usedSubtreeIdsAtMO = checkUnUsedSubTreeRef(xmlProcessor, usedSubTreeRefIdsMO);
			
			// Stratifications
			
			String startSubTreeRefID = "/measure/strata//clause["+
					uuidXPathString+"]//subTreeRef[not(@id = preceding:: strata//clause//subTreeRef/@id)]/@id";
			groupedSubTreeRefIdListStrat = (NodeList) xPath.evaluate(startSubTreeRefID,
					xmlProcessor.getOriginalDoc(), XPathConstants.NODESET);
			
			for (int i = 0; i < groupedSubTreeRefIdListStrat.getLength(); i++) {
				Node groupedSubTreeRefIdAttributeNodeStrat = groupedSubTreeRefIdListStrat
						.item(i);
				String uuid = groupedSubTreeRefIdAttributeNodeStrat.getNodeValue();
				uuid = checkIfQDMVarInstanceIsPresent(uuid, xmlProcessor);
				if(!usedSubTreeRefIdsStrat.contains(uuid)) {
					usedSubTreeRefIdsStrat.add(uuid);
				}
			}
			
			//get used Subtreeids at Stratification
			List<String> usedSubtreeIdsAtStrat = checkUnUsedSubTreeRef(xmlProcessor, usedSubTreeRefIdsStrat);
			
			//			usedSubTreeRefIdsPop.removeAll(usedSubTreeRefIdsMO);
			//			usedSubTreeRefIdsMO.addAll(usedSubTreeRefIdsPop);
			//
			//			usedSubTreeRefIdsMO.removeAll(usedSubTreeRefIdsStrat);
			//			usedSubTreeRefIdsStrat.addAll(usedSubTreeRefIdsMO);
			
			usedSubTreeIdsMap.put("subTreeIDAtPop", usedSubtreeIdsAtPop);
			usedSubTreeIdsMap.put("subTreeIDAtMO", usedSubtreeIdsAtMO);
			usedSubTreeIdsMap.put("subTreeIDAtStrat", usedSubtreeIdsAtStrat);
			
		} catch (XPathExpressionException e) {
			
			e.printStackTrace();
		}
		
		return usedSubTreeIdsMap;
	}
	
	/**
	 * Check un used sub tree ref.
	 *
	 * @param xmlProcessor the xml processor
	 * @param usedSubTreeRefIds the used sub tree ref ids
	 * @return the list
	 */
	private  List<String> checkUnUsedSubTreeRef(XmlProcessor xmlProcessor, List<String> usedSubTreeRefIds){
		
		List<String> allSubTreeRefIds = new ArrayList<String>();
		NodeList subTreeRefIdsNodeList;
		try {
			subTreeRefIdsNodeList = (NodeList) xPath.evaluate(
					"/measure//subTreeRef/@id", xmlProcessor.getOriginalDoc(),
					XPathConstants.NODESET);
			
			
			for (int i = 0; i < subTreeRefIdsNodeList.getLength(); i++) {
				Node SubTreeRefIdAttributeNode = subTreeRefIdsNodeList.item(i);
				if (!allSubTreeRefIds.contains(SubTreeRefIdAttributeNode
						.getNodeValue())) {
					allSubTreeRefIds.add(SubTreeRefIdAttributeNode.getNodeValue());
				}
			}
			allSubTreeRefIds.removeAll(usedSubTreeRefIds);
			
			for (int i = 0; i < usedSubTreeRefIds.size(); i++) {
				for (int j = 0; j < allSubTreeRefIds.size(); j++) {
					Node usedSubTreeRefNode = (Node) xPath.evaluate(
							"/measure/subTreeLookUp/subTree[@uuid='"
									+ usedSubTreeRefIds.get(i)
									+ "']//subTreeRef[@id='"
									+ allSubTreeRefIds.get(j) + "']",
									xmlProcessor.getOriginalDoc(), XPathConstants.NODE);
					
					if (usedSubTreeRefNode != null) {
						
						String subTreeUUID = usedSubTreeRefNode.getAttributes().getNamedItem("id").getNodeValue();
						String XPATH_IS_INSTANCE_OF = "//subTree [boolean(@instanceOf)]/@uuid ='"
								+ subTreeUUID +"'";
						boolean isOccurrenceNode = (Boolean) xPath.evaluate(XPATH_IS_INSTANCE_OF, xmlProcessor.getOriginalDoc(), XPathConstants.BOOLEAN);
						if(isOccurrenceNode) {
							String XPATH_PARENT_UUID = "//subTree [@uuid ='"+subTreeUUID +"']/@instanceOf";
							String parentUUID = (String) xPath.evaluate(XPATH_PARENT_UUID, xmlProcessor.getOriginalDoc(), XPathConstants.STRING);
							if (!usedSubTreeRefIds.contains(parentUUID)) {
								usedSubTreeRefIds.add(parentUUID);
							}
							
						}
						if (!usedSubTreeRefIds.contains(allSubTreeRefIds.get(j))) {
							
							
							usedSubTreeRefIds.add(allSubTreeRefIds.get(j));
						}
					}
				}
				
			}
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return usedSubTreeRefIds;
	}
	/**
	 * Validate qdm node.
	 *
	 * @param qdmchildNode the qdmchild node
	 * @param attributeValue the attribute value
	 * @return true, if successful
	 */
	private boolean validateQdmNode(Node qdmchildNode, String attributeValue) {
		boolean flag = true;
		String dataTypeValue = qdmchildNode.getAttributes()
				.getNamedItem("datatype").getNodeValue();
		String qdmName = qdmchildNode.getAttributes().getNamedItem("name")
				.getNodeValue();
		String oidValue = qdmchildNode.getAttributes().getNamedItem("oid").getNodeValue();
		if (dataTypeValue.equalsIgnoreCase("timing element")) {
			if (qdmName.equalsIgnoreCase("Measurement End Date")
					|| qdmName.equalsIgnoreCase("Measurement Start Date")) {
				flag = false;
			}
		}
		else if(dataTypeValue.equalsIgnoreCase("Patient characteristic Birthdate") || dataTypeValue.equalsIgnoreCase("Patient characteristic Expired")){
			
			if(oidValue.equalsIgnoreCase("419099009") || oidValue.equalsIgnoreCase("21112-8")){
				//do nothing
			}else{
				flag = false;
			}
		}
		//
		else if (attributeValue.isEmpty()) {
			if (!checkIfQDMDataTypeIsPresent(dataTypeValue)) {
				flag = false;
			}
			
		}
		else if (!attributeValue.isEmpty() && (attributeValue.length() > 0)) {
			if (checkIfQDMDataTypeIsPresent(dataTypeValue)) {
				
				List<QDSAttributes> attlibuteList = getAllDataTypeAttributes(dataTypeValue);
				if ((attlibuteList.size() > 0) && (attlibuteList != null)) {
					List<String> attrList = new ArrayList<String>();
					for (int i = 0; i < attlibuteList.size(); i++) {
						attrList.add(attlibuteList.get(i).getName());
					}
					if (!attrList.contains(attributeValue)) {
						flag = false;
					}
				}
				
			} else {
				flag = false;
			}
		}
		return flag;
	}
	
	
	
	/**
	 * Validate Timing and Relationship node.
	 *
	 * @param timingElementchildNode the timing elementchild node
	 * @param operatorTypeList the operator type list
	 * @param flag the flag
	 * @return true, if successful
	 */
	private boolean validateTimingRelationshipNode(Node timingElementchildNode, List<String> operatorTypeList, boolean flag) {
		int childCount = timingElementchildNode.getChildNodes().getLength();
		String type = timingElementchildNode.getAttributes().getNamedItem("type").getNodeValue();
		if((childCount != 2) || !operatorTypeList.contains(type)){
			flag = true;
		}
		return flag;
	}
	
	
	/**
	 * Validate satisfy node.
	 *
	 * @param satisfyElementchildNode the satisfy elementchild node
	 * @param flag the flag
	 * @return true, if successful
	 */
	private boolean validateSatisfyNode(Node satisfyElementchildNode, boolean flag) {
		int childCount = satisfyElementchildNode.getChildNodes().getLength();
		if(childCount < 2){
			flag = true;
		}
		return flag;
	}
	
	/**
	 * Validate function node.
	 *
	 * @param functionchildNode the functionchild node
	 * @param operatorTypeList the operator type list
	 * @param flag the flag
	 * @return true, if successful
	 */
	private boolean validateFunctionNode(Node functionchildNode,  List<String> operatorTypeList, boolean flag){
		int functionChildCount = functionchildNode.getChildNodes().getLength();
		String type = functionchildNode.getAttributes().getNamedItem("type").getNodeValue();
		if((functionChildCount< 1) || !operatorTypeList.contains(type)){
			flag = true;
		}
		return flag;
		
	}
	
	/**
	 * Validate set operator node.
	 *
	 * @param SetOperatorchildNode the set operatorchild node
	 * @param flag the flag
	 * @return true, if successful
	 */
	private boolean validateSetOperatorNode(Node SetOperatorchildNode, boolean flag){
		int setOperatorChildCount = SetOperatorchildNode.getChildNodes().getLength();
		if(setOperatorChildCount< 1){
			flag = true;
		}
		return flag;
		
	}
	
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#validateForGroup(mat.client.measure.ManageMeasureDetailModel)
	 */
	@Override
	public ValidateMeasureResult validateForGroup(ManageMeasureDetailModel model) {
		
		logger.debug(" MeasureLibraryServiceImpl: validateGroup Start :  ");
		
		List<String> message = new ArrayList<String>();
		ValidateMeasureResult result = new ValidateMeasureResult();
		MeasureXmlModel xmlModel = getService().getMeasureXmlForMeasure(model.getId());
		
		if (((xmlModel != null) && StringUtils.isNotBlank(xmlModel.getXml()))) {
			XmlProcessor xmlProcessor = new XmlProcessor(xmlModel.getXml());
			/*System.out.println("MEASURE_XML: "+xmlModel.getXml());*/
			
			//validate for at least one grouping
			String XPATH_GROUP = "/measure/measureGrouping/group";
			
			NodeList groupSDE;
			try {
				groupSDE = (NodeList) xPath.evaluate(XPATH_GROUP, xmlProcessor.getOriginalDoc(),
						XPathConstants.NODESET);
				
				if(groupSDE.getLength()==0){
					message.add(MatContext.get().getMessageDelegate().getGroupingRequiredMessage());
					
				}else{
					for(int i=1; i<=groupSDE.getLength(); i++){
						NodeList numberOfStratificationPerGroup = (NodeList) xPath.evaluate("/measure/measureGrouping/group[@sequence='"+i+"']/packageClause[@type='stratification']", xmlProcessor.getOriginalDoc(),
								XPathConstants.NODESET);
						if(numberOfStratificationPerGroup.getLength()>1){
							message.add(MatContext.get().getMessageDelegate().getSTRATIFICATION_VALIDATION_FOR_GROUPING());
							break;
						}
					}
					
				}
			} catch (XPathExpressionException e2) {
				
				e2.printStackTrace();
			}
		}
		
		result.setValid(message.size() == 0);
		result.setValidationMessages(message);
		return result;
	}
	
	/**
	 * Takes an XPath notation String for a particular tag and a Document object
	 * and finds and removes the tag from the document.
	 * @param nodeXPath the node x path
	 * @param originalDoc the original doc
	 * @throws XPathExpressionException the x path expression exception
	 */
	private void removeNode(String nodeXPath, Document originalDoc) throws XPathExpressionException {
		Node node = (Node)xPath.evaluate(nodeXPath, originalDoc.getDocumentElement(), XPathConstants.NODE);
		if(node != null){
			Node parentNode = node.getParentNode();
			parentNode.removeChild(node);
		}
	}
	
	/**
	 * Update node.
	 *
	 * @param nodeXPath the node x path
	 * @param originalDoc the original doc
	 * @throws XPathExpressionException the x path expression exception
	 */
	private void updateNode(String nodeXPath, Document originalDoc) throws XPathExpressionException {
		Node node = (Node)xPath.evaluate(nodeXPath, originalDoc.getDocumentElement(), XPathConstants.NODE);
		if(node != null){
			Node parentNode = node.getParentNode();
			parentNode.removeChild(node);
		}
	}
	
	
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getAppliedQDMForItemCount(java.lang.String, boolean)
	 */
	@Override
	public final List<QualityDataSetDTO> getAppliedQDMForItemCount(final String measureId,
			final boolean checkForSupplementData){
		
		List<QualityDataSetDTO> qdmList = getAppliedQDMFromMeasureXml(measureId, checkForSupplementData);
		List<QualityDataSetDTO> filterQDMList = new ArrayList<QualityDataSetDTO>();
		DataTypeDAO dataTypeDAO = (DataTypeDAO)context.getBean("dataTypeDAO");
		for (QualityDataSetDTO qdsDTO : qdmList) {
			DataType dataType = dataTypeDAO.findByDataTypeName(qdsDTO.getDataType());
			if ("Timing Element".equals(qdsDTO
					.getDataType()) || "attribute".equals(qdsDTO.getDataType())
					|| ConstantMessages.PATIENT_CHARACTERISTIC_BIRTHDATE.equals(qdsDTO
							.getDataType()) || ConstantMessages.PATIENT_CHARACTERISTIC_EXPIRED.equals(qdsDTO
									.getDataType()) || (dataType == null)) {
				filterQDMList.add(qdsDTO);
			}
		}
		
		qdmList.removeAll(filterQDMList);
		return qdmList;
		
	}
	
	/**
	 * Gets the all measure types.
	 *
	 * @return the all measure types
	 */
	@Override
	public final List<MeasureType> getAllMeasureTypes(){
		List<MeasureTypeDTO> measureTypeDTOList = measureTypeDAO.getAllMeasureTypes();
		List<MeasureType> measureTypeList = new ArrayList<MeasureType>();
		for(MeasureTypeDTO measureTypeDTO : measureTypeDTOList){
			MeasureType measureType = new MeasureType();
			measureType.setDescription(measureTypeDTO.getName());
			measureType.setAbbrDesc(MeasureDetailsUtil.getMeasureTypeAbbr(measureTypeDTO.getName()));
			measureTypeList.add(measureType);
		}
		return measureTypeList;
		
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getAllAuthors()
	 */
	@Override
	public List<Organization> getAllOrganizations(){
		List<Organization> organizationDTOList = organizationDAO.getAllOrganizations();
		return organizationDTOList;
	}
	
	/**
	 * Gets the all operators type list.
	 *
	 * @return the all operators type list
	 */
	private List<String> getAllOperatorsTypeList() {
		List<OperatorDTO> allOperatorsList = operatorDAO.getAllOperators();
		List<String> allOperatorsTypeList = new ArrayList<String>();
		for(int i = 0; i<allOperatorsList.size(); i ++){
			allOperatorsTypeList.add(allOperatorsList.get(i).getId());
		}
		return allOperatorsTypeList;
	}
	
	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getUsedStewardAndDevelopersList(java.lang.String)
	 */
	@Override
	public MeasureDetailResult getUsedStewardAndDevelopersList(String measureId) {
		logger.info("In MeasureLibraryServiceImpl.getUsedStewardAndDevelopersList() method..");
		logger.info("Loading Measure for MeasueId: " + measureId);
		Measure measure = getService().getById(measureId);
		MeasureDetailResult usedStewardAndAuthorList = new MeasureDetailResult();
		MeasureXmlModel xml = getMeasureXmlForMeasure(measureId);
		usedStewardAndAuthorList.setUsedAuthorList(getAuthorsList(xml));
		usedStewardAndAuthorList.setUsedSteward(getSteward(xml));
		usedStewardAndAuthorList.setAllAuthorList(getAllAuthorList());
		usedStewardAndAuthorList.setAllStewardList(getAllStewardList());
		return usedStewardAndAuthorList;
		
	}
	
	/**
	 * Gets the all steward list.
	 *
	 * @return the all steward list
	 */
	private List<MeasureSteward> getAllStewardList() {
		List<MeasureSteward> stewardList = new ArrayList<MeasureSteward>();
		List<Organization> organizationList = getAllOrganizations();
		for(Organization org:organizationList){
			MeasureSteward steward= new MeasureSteward();
			steward.setId(Long.toString(org.getId()));
			steward.setOrgName(org.getOrganizationName());
			steward.setOrgOid(org.getOrganizationOID());
			stewardList.add(steward);
		}
		return stewardList;
	}
	
	/**
	 * Gets the all author list.
	 *
	 * @return the all author list
	 */
	private List<Author> getAllAuthorList() {
		List<Author> authorList = new ArrayList<Author>();
		List<Organization> organizationList = getAllOrganizations();
		for(Organization org:organizationList){
			Author author= new Author();
			author.setId(Long.toString(org.getId()));
			author.setAuthorName(org.getOrganizationName());
			author.setOrgId(org.getOrganizationOID());
			authorList.add(author);
		}
		
		return authorList;
	}
	
	/**
	 * Gets the authors list.
	 *
	 * @param xmlModel the xml model
	 * @return the authors list
	 */
	private List<Author> getAuthorsList(MeasureXmlModel xmlModel) {
		XmlProcessor processor = new XmlProcessor(xmlModel.getXml());
		String XPATH_EXPRESSION_DEVELOPERS = "/measure//measureDetails//developers";
		List<Author> authorList = new ArrayList<Author>();
		List<Author> usedAuthorList = new ArrayList<Author>();
		List<Organization> allOrganization = getAllOrganizations();
		try {
			
			NodeList developerParentNodeList = (NodeList) xPath.evaluate(
					XPATH_EXPRESSION_DEVELOPERS, processor.getOriginalDoc(),
					XPathConstants.NODESET);
			Node developerParentNode = developerParentNodeList.item(0);
			if (developerParentNode != null) {
				NodeList developerNodeList = developerParentNode
						.getChildNodes();
				
				for (int i = 0; i < developerNodeList.getLength(); i++) {
					Author author = new Author();
					String developerId = developerNodeList.item(i).getAttributes()
							.getNamedItem("id").getNodeValue();
					String AuthorValue = developerNodeList.item(i).getTextContent();
					author.setId(developerId);
					author.setAuthorName(AuthorValue);
					authorList.add(author);
					
				}
				//if deleted, remove from the list
				for(Organization org:allOrganization){
					for(int i=0;i<authorList.size();i++){
						if(authorList.get(i).getId().equalsIgnoreCase(Long.toString(org.getId()))){
							usedAuthorList.add(authorList.get(i));
						}
					}
				}
			}
			
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return usedAuthorList;
	}
	
	/**
	 * Gets the steward id.
	 *
	 * @param xmlModel the xml model
	 * @return the steward id
	 */
	private MeasureSteward getSteward(MeasureXmlModel xmlModel) {
		MeasureSteward measureSteward = new MeasureSteward();
		XmlProcessor processor = new XmlProcessor(xmlModel.getXml());
		List<Organization> allOrganization = getAllOrganizations();
		String XPATH_EXPRESSION_STEWARD = "/measure//measureDetails//steward";
		
		try {
			Node stewardParentNode = (Node) xPath.evaluate(
					XPATH_EXPRESSION_STEWARD, processor.getOriginalDoc(),
					XPathConstants.NODE);
			if (stewardParentNode != null) {
				String id = stewardParentNode.getAttributes()
						.getNamedItem("id").getNodeValue();
				for(Organization org:allOrganization){
					if(id.equalsIgnoreCase(Long.toString(org.getId()))){
						measureSteward.setId(id);
						measureSteward.setOrgName(org.getOrganizationName());
						measureSteward.setOrgOid(org.getOrganizationOID());
					}
				}
				
			}
			
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return measureSteward;
		
	}
	
	/**
	 * Checks if is measure created.
	 *
	 * @return true, if is measure created
	 */
	public boolean isMeasureCreated() {
		return isMeasureCreated;
	}

	/**
	 * Sets the measure created.
	 *
	 * @param isMeasureCreated the new measure created
	 */
	public void setMeasureCreated(boolean isMeasureCreated) {
		this.isMeasureCreated = isMeasureCreated;
	}

	/* (non-Javadoc)
	 * @see mat.server.service.MeasureLibraryService#getReleaseDate()
	 */
	@Override
	public String getReleaseDate() {
		return releaseDate;
	}
	
	/**
	 * Sets the release date.
	 *
	 * @param releaseDate the new release date
	 */
	public void setReleaseDate(String releaseDate) {
		this.releaseDate = releaseDate;
	}
		
}

