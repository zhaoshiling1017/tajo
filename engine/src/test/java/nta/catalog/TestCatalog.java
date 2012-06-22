package nta.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import nta.catalog.proto.CatalogProtos.DataType;
import nta.catalog.proto.CatalogProtos.FunctionType;
import nta.catalog.proto.CatalogProtos.StoreType;
import nta.datum.Datum;
import nta.engine.NConstants;
import nta.engine.NtaEngineMaster;
import nta.engine.NtaTestingUtility;
import nta.engine.function.GeneralFunction;
import nta.storage.CSVFile2;
import nta.storage.Tuple;
import nta.util.FileUtil;
import nta.zookeeper.ZkClient;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author Hyunsik Choi
 *
 */
public class TestCatalog {	
	static final String FieldName1="f1";
	static final String FieldName2="f2";
	static final String FieldName3="f3";	

	Schema schema1;
	
	static NtaTestingUtility util;
	static CatalogService catalog;
	
	static String TEST_PATH = "target/test-data/TestCatalog";
	
	@BeforeClass
	public static void setUp() throws Exception {
	  util = new NtaTestingUtility();
    util.startMiniCluster(3);
    catalog = util.getMiniTajoCluster().getMaster().getCatalog();
	}
	
	@AfterClass
	public static void tearDown() throws IOException {
	  util.shutdownMiniCluster();
	}
	
	@Test
	public void testGetTable() throws Exception {
		schema1 = new Schema();
		schema1.addColumn(FieldName1, DataType.BYTE);
		schema1.addColumn(FieldName2, DataType.INT);
		schema1.addColumn(FieldName3, DataType.LONG);
		
		TableDesc meta = TCatUtil.newTableDesc(
		    "getTable", 
		    schema1, 
		    StoreType.CSV,
		    new Options(),
		    new Path("/table1"));
		
		assertFalse(catalog.existsTable("getTable"));
		catalog.addTable(meta);
		assertTrue(catalog.existsTable("getTable"));
		
		TableDesc meta2 = catalog.getTableDesc("getTable");
		System.out.println(meta2);
		
		catalog.deleteTable("getTable");
		assertFalse(catalog.existsTable("getTable"));
	}
	
	@Test(expected = Throwable.class)
	public void testAddTableNoName() throws Exception {
	  schema1 = new Schema();
    schema1.addColumn(FieldName1, DataType.BYTE);
    schema1.addColumn(FieldName2, DataType.INT);
    schema1.addColumn(FieldName3, DataType.LONG);
    
	  TableMeta info = TCatUtil.newTableMeta(schema1, StoreType.CSV);
	  TableDesc desc = new TableDescImpl();
	  desc.setMeta(info);
	  
	  catalog.addTable(desc);
	  
	  util.shutdownCatalogCluster();
    util.shutdownMiniZKCluster();
	}
	
	@Test
	public void testAddAndDelIndex() throws Exception {
	  TableDesc desc = TestDBStore.prepareTable();
	  catalog.addTable(desc);
	  
	  assertFalse(catalog.existIndex(TestIndexDesc.desc1.getName()));
	  assertFalse(catalog.existIndex("indexed", "id"));
	  catalog.addIndex(TestIndexDesc.desc1);
	  assertTrue(catalog.existIndex(TestIndexDesc.desc1.getName()));
	  assertTrue(catalog.existIndex("indexed", "id"));
	  
	  assertFalse(catalog.existIndex(TestIndexDesc.desc2.getName()));
	  assertFalse(catalog.existIndex("indexed", "score"));
	  catalog.addIndex(TestIndexDesc.desc2);
	  assertTrue(catalog.existIndex(TestIndexDesc.desc2.getName()));
	  assertTrue(catalog.existIndex("indexed", "score"));
	  
	  catalog.deleteIndex(TestIndexDesc.desc1.getName());
	  assertFalse(catalog.existIndex(TestIndexDesc.desc1.getName()));
	  catalog.deleteIndex(TestIndexDesc.desc2.getName());
	  assertFalse(catalog.existIndex(TestIndexDesc.desc2.getName()));
	  
	  catalog.deleteTable(desc.getId());
	}
	
	public static class TestFunc1 extends GeneralFunction {
    private Datum param;
		public TestFunc1() {
			super(					
					new Column [] {
							new Column("name", DataType.INT)
					}
			);
		}

    @Override
    public Datum eval(Tuple params) {
      return params.get(0);
    }
	}
	
	 public static class TestFunc2 extends GeneralFunction {
     private Datum param;
	    public TestFunc2() {
	      super(          
	          new Column [] {
	              new Column("name", DataType.INT),
	              new Column("bytes", DataType.BYTES)
	          }
	      );
	    }

     @Override
     public Datum eval(Tuple params) {
       return params.get(1);
     }
	  } 

	@Test
	public final void testRegisterFunc() throws Exception { 
		assertFalse(catalog.containFunction("test2"));
		FunctionDesc meta = new FunctionDesc("test2", TestFunc1.class, FunctionType.GENERAL,
        new DataType [] {DataType.INT},
		    new DataType [] {DataType.INT});

    catalog.registerFunction(meta);
		assertTrue(catalog.containFunction("test2", DataType.INT));
		FunctionDesc retrived = catalog.getFunction("test2", DataType.INT);

		assertEquals(retrived.getSignature(),"test2");
		assertEquals(retrived.getFuncClass(),TestFunc1.class);
		assertEquals(retrived.getFuncType(),FunctionType.GENERAL);
	}

  @Test
  public final void testUnregisterFunc() throws Exception {    
    assertFalse(catalog
        .containFunction("test3", DataType.INT));
    FunctionDesc meta = new FunctionDesc("test3", TestFunc1.class, FunctionType.GENERAL,
        new DataType [] {DataType.INT}, new DataType[] { DataType.INT });
    catalog.registerFunction(meta);
    assertTrue(catalog.containFunction("test3", DataType.INT));
    catalog.unregisterFunction("test3", DataType.INT);
    assertFalse(catalog
        .containFunction("test3", DataType.INT));

    assertFalse(catalog.containFunction("test3", DataType.INT, DataType.BYTES));
    FunctionDesc overload = new FunctionDesc("test3", TestFunc2.class, FunctionType.GENERAL,
        new DataType [] {DataType.INT},
        new DataType [] {DataType.INT,DataType.BYTES});
    catalog.registerFunction(overload);
    assertTrue(catalog.containFunction("test3", DataType.INT, DataType.BYTES));
  }
	
	@Test
	public final void testHostsByTable() throws Exception {  
	  CatalogService local = util.getMiniTajoCluster().
	      getMaster().getCatalog();
	  
		int i, j;
		FSDataOutputStream fos;
		Path tbPath;
		
		NtaEngineMaster master = util.getMiniTajoCluster().getMaster();
		
		Schema schema = new Schema();
		schema.addColumn("id",DataType.INT);
		schema.addColumn("age",DataType.INT);
		schema.addColumn("name",DataType.STRING);

		TableMeta meta;

		String [] tuples = {
				"1,32,hyunsik",
				"2,29,jihoon",
				"3,28,jimin",
				"4,24,haemi"
		};

		FileSystem fs = util.getMiniDFSCluster().getFileSystem();

		int tbNum = 5;
		Random random = new Random();
		int tupleNum;
		
		for (i = 0; i < tbNum; i++) {
			tbPath = new Path(TEST_PATH+"/HostsByTable"+i);
			if (fs.exists(tbPath)){
				fs.delete(tbPath, true);
			}
			fs.mkdirs(tbPath);
			fos = fs.create(new Path(tbPath, ".meta"));
			meta = TCatUtil.newTableMeta(schema, StoreType.CSV);
			meta.putOption(CSVFile2.DELIMITER, ",");			
			FileUtil.writeProto(fos, meta.getProto());
			fos.close();
			
			fos = fs.create(new Path(tbPath, "data/table.csv"));
			tupleNum = random.nextInt(49)+100001;
			for (j = 0; j < tupleNum; j++) {
				fos.writeBytes(tuples[0]+"\n");
			}
			fos.close();

			TableDesc desc = new TableDescImpl("HostsByTable"+i, meta, tbPath);
			local.addTable(desc);
		}
		
		local.updateAllTabletServingInfo(master.getOnlineServer());
		
		Collection<String> tables = local.getAllTableNames();
		Iterator<String> it = tables.iterator();
		List<FragmentServInfo> tabletInfoList;
		int cnt = 0;
		int len = 0;
		TableDesc tableInfo;
		FileStatus fileStatus;
		while (it.hasNext()) {
			tableInfo = local.getTableDesc(it.next());
			tabletInfoList = local.getHostByTable(tableInfo.getId());
			if (tabletInfoList != null) {
				cnt++;
				len = 0;
				for (i = 0; i < tabletInfoList.size(); i++) {
					len += tabletInfoList.get(i).getFragment().getLength();
				}
				fileStatus = fs.getFileStatus(new Path(tableInfo.
				    getPath()+"/data/table.csv"));
				assertEquals(len, fileStatus.getLen());
			}
		}
		
		assertEquals(tbNum, cnt);
	}
	
	@Test	
	public void testInitializeZookeeper() throws Exception { 
    ZkClient zkClient = new ZkClient(util.getConfiguration());
    assertTrue(zkClient.exists(NConstants.ZNODE_CATALOG) != null);
	}
}