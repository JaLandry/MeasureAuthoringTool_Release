package mat.server.service;

import java.util.List;

import mat.client.clause.clauseworkspace.model.MeasureXmlModel;
import mat.client.measure.ManageMeasureShareModel;
import mat.client.measure.service.ValidateMeasureResult;
import mat.model.DataType;
import mat.model.QualityDataSet;
import mat.model.clause.Measure;
import mat.model.clause.MeasureSet;
import mat.model.clause.MeasureShareDTO;

public interface MeasurePackageService {
	public long count();
	public long count(String searchText);
	public List<MeasureShareDTO> search(int startIndex, int numResults);
	public List<MeasureShareDTO> searchMeasuresForVersion(int startIndex, int numResults);
	public long countMeasuresForVersion();
	public long countMeasuresForDraft();
	public List<MeasureShareDTO> searchMeasuresForDraft(int startIndex, int numResults);
	public List<MeasureShareDTO> search(String searchText, int startIndex, int numResults);
	public void save(Measure measurePackage);
	
	public Measure getById(String id);
	
	
	public String findOutMaximumVersionNumber(String measureSetId);
	public String findOutVersionNumber(String measureId, String measureSetId);
	public List<MeasureShareDTO> getUsersForShare(String measureId, int startIndex, int pageSize);
	public int countUsersForMeasureShare();
	public void updateUsersShare(ManageMeasureShareModel model);
	public void updateLockedOutDate(Measure m);
	public ValidateMeasureResult validateMeasureForExport(String key) throws Exception;
	public MeasureSet findMeasureSet(String id);
	public void save(MeasureSet measureSet);
	public String getUniqueOid();
	public DataType findDataTypeForSupplimentalCodeList(String dataTypeName,String categoryId);
	public void deleteExistingPackages(String measureId);
	public void saveSupplimentalQDM(QualityDataSet qds);
	public boolean isMeasureLocked(String id);
	public int getMaxEMeasureId();
	public int saveAndReturnMaxEMeasureId(Measure measure);
	void transferMeasureOwnerShipToUser(List<String> list, String toEmail);
	List<MeasureShareDTO> searchWithFilter(String searchText, int startIndex,
			int numResults, int filter);
	long count(int filter);
	public MeasureXmlModel getMeasureXmlForMeasure(String measureId);
	public void saveMeasureXml(MeasureXmlModel measureXmlModel);
	public String retrieveStewardOID(String stewardName);
	public void updatePrivateColumnInMeasure(String measureId, boolean isPrivate);
}
