package com.keer.bdql.BDQLParser;

import com.bigchaindb.model.Asset;
import com.bigchaindb.model.Assets;

import com.bigchaindb.model.Transaction;
import com.keer.bdql.Bigchaindb.BigchainDBRunner;
import com.keer.bdql.Bigchaindb.BigchainDBUtil;
import com.keer.bdql.pojo.BigchainDBData;
import com.keer.bdql.pojo.MetaData;
import com.keer.bdql.pojo.WebResult;
import com.keer.bdql.pojo.Table;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class BDQLParser {
    private static Logger logger = LoggerFactory.getLogger(BDQLParser.class);

    @Autowired
    BigchainDBRunner bigchainDBRunner;

    @Autowired
    BigchainDBUtil bigchainDBUtil;

    @Autowired
    BDQLUtil bdqlUtil;

    /**
     * 根据不同的类型的BDQL进行不同的解析,根据分号区分语句个数
     *
     * @param BDQL
     */
    public WebResult BDQLParser(String BDQL) {

        WebResult result = new WebResult();
        logger.info("开始解析BDQL：" + BDQL + "，##########################");
        Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(BDQL);
        } catch (JSQLParserException e) {
            logger.error("BDQL语法错误原因：" + getJSQLParserExceptionCauseBy(e));
            e.printStackTrace();
        }
        if (statement instanceof Select) {
            return selectParser((Select) statement);
        } else if (statement instanceof Insert) {
            return insertParser((Insert) statement);
        } else if (statement instanceof Update) {
            return updateParser((Update) statement);
        }
        result.setCode(WebResult.ERROR);
        result.setMessage("此种语法还未推出！！！");
        logger.error("此种语法还未推出！！！");
        return result;
    }


    /**
     * 开始解析select语句
     *
     * @param select Select
     */
    private WebResult selectParser(Select select) {
        WebResult result = new WebResult();
        Table table = new Table();
        //获得where 条件
        PlainSelect selectBody = (PlainSelect) select.getSelectBody();
        Expression expression = selectBody.getWhere();
        //获得表名
        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tableNames = tablesNamesFinder.getTableList(select);

        /**
         * 如果是多表查询，直接返回不支持
         */
        if (tableNames.size() != 1) {
            logger.warn("多表查询功能还未推出！！！！");
            result.setCode(WebResult.ERROR);
            result.setMessage("多表查询功能还未推出！！！！");
            return result;
        } else {
            table.setTableName(tableNames.get(0));
            //获得列名
            ArrayList<String> columnNames = (ArrayList<String>) getColumnNames(selectBody);
            /**
             * 目前如果where中有关键字 ID 表示直接交易id 的这笔交易
             */
            if (expression instanceof EqualsTo && ((EqualsTo) expression).getLeftExpression().toString().equals("ID")) {
                String TXID = ((EqualsTo) expression).getRightExpression().toString();
                Transaction transaction = bigchainDBUtil.getTransactionByTXID(TXID);
                if (transaction.getOperation().equals("CREATE")) {
                    table.setType("CREATE");
                    Assets assets = new Assets();
                    Asset asset = transaction.getAsset();
                    assets.addAsset(asset);
                    table.setTableDataWithColumnName(assets);

                } else {
                    table.setType("TRANSFER");
                    List<MetaData> metaDatas = new LinkedList<MetaData>();
                    MetaData metaData = (MetaData) transaction.getMetaData();
                    metaDatas.add(metaData);
                    table.setTableDataWithCloumnName(metaDatas);
                }

            } else {
                /**
                 * 支持*查询
                 */
                if (columnNames.size() == 1 && columnNames.get(0).equals("*")) {
                    if (bigchainDBUtil.getAssetByKey(table.getTableName()).size() == 0) {
                        List<MetaData> metaDatas = bigchainDBUtil.getMetaDatasByKey(table.getTableName());
                        table.setType("TRANSFER");
                        List<MetaData> newMetadatas=selectMetadata(metaDatas,expression);
                        table.setTableDataWithCloumnName(newMetadatas);
                    } else {
                        Assets assets = bigchainDBUtil.getAssetByKey(table.getTableName());
                        table.setType("CREATE");
                        Assets newAssets=selectAssets(assets,expression);
                        table.setTableDataWithColumnName(newAssets);
                    }

                } else {
                    if (bigchainDBUtil.getAssetByKey(table.getTableName()).size() == 0) {
                        List<MetaData> metaDatas = bigchainDBUtil.getMetaDatasByKey(table.getTableName());
                        table.setType("TRANSFER");
                        table.setColumnName(columnNames);
                        List<MetaData> newMetadatas=selectMetadata(metaDatas,expression);
                        table.setTableData(newMetadatas);

                    } else {
                        Assets assets = bigchainDBUtil.getAssetByKey(table.getTableName());
                        table.setType("CREATE");
                        table.setColumnName(columnNames);
                        Assets newAssets=selectAssets(assets,expression);
                        table.setTableData(newAssets);

                    }
                }
            }

            result.setCode(WebResult.CODE_SUCCESS);
            result.setData(table);
            result.setMessage("select");
            return result;
        }


    }

    /**
     * 获得select的查询列名
     *
     * @param selectBody
     * @return
     */
    private   List<String> getColumnNames(SelectBody selectBody) {
        PlainSelect plainSelect = (PlainSelect) selectBody;
        //获得查询的列名
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        List<String> str_items = new ArrayList<String>();
        if (selectItems != null) {
            for (int i = 0; i < selectItems.size(); i++) {
                str_items.add(selectItems.get(i).toString());
            }
        }
        return str_items;
    }

    /**
     * 根据where挑选asset
     * @param assets
     * @param expression
     * @return
     */
    private   Assets selectAssets(Assets assets,Expression expression){
        if(expression==null){
            return assets;
        }
        Assets newAssets=new Assets();
        if (expression instanceof EqualsTo) {
            String left = ((EqualsTo) expression).getLeftExpression().toString();
            String right = ((EqualsTo) expression).getRightExpression().toString();
            for (Asset asset : assets.getAssets()) {
                Map map=(Map) asset.getData();
                Map map1=(Map) map.get("tableData");
                if(map1.get(left).toString().equals(right)){
                    newAssets.addAsset(asset);
                }
            }

        }
        if (expression instanceof GreaterThan) {
            String left = ((GreaterThan) expression).getLeftExpression().toString();
            String right = ((GreaterThan) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (Asset asset : assets.getAssets()) {
                Map map=(Map) asset.getData();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())>R){
                    newAssets.addAsset(asset);
                }
            }


        }
        if (expression instanceof GreaterThanEquals) {
            String left = ((GreaterThanEquals) expression).getLeftExpression().toString();
            String right = ((GreaterThanEquals) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (Asset asset : assets.getAssets()) {
                Map map=(Map) asset.getData();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())>=R){
                    newAssets.addAsset(asset);
                }
            }

        }
        if (expression instanceof MinorThan) {
            String left = ((MinorThan) expression).getLeftExpression().toString();
            String right = ((MinorThan) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (Asset asset : assets.getAssets()) {
                Map map=(Map) asset.getData();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())<R){
                    newAssets.addAsset(asset);
                }
            }

        }
        if (expression instanceof MinorThanEquals) {
            String left = ((MinorThanEquals) expression).getLeftExpression().toString();
            String right = ((MinorThanEquals) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (Asset asset : assets.getAssets()) {
                Map map=(Map) asset.getData();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())<=R){
                    newAssets.addAsset(asset);
                }
            }

        }
        return newAssets;

    }

    /**
     * 根据where挑选metadata
     * @param metaDataList
     * @param expression
     * @return
     */
    private   List<MetaData> selectMetadata(List<MetaData> metaDataList,Expression expression){
        if(expression==null){
            return metaDataList;
        }
        List<MetaData> newMetadata=new ArrayList<>();
        if (expression instanceof EqualsTo) {
            String left = ((EqualsTo) expression).getLeftExpression().toString();
            String right = ((EqualsTo) expression).getRightExpression().toString();
            for (MetaData metaData : metaDataList) {
                Map map= metaData.getMetadata();
                Map map1=(Map) map.get("tableData");
                if(map1.get(left).toString().equals(right)){
                    newMetadata.add(metaData);
                }
            }

        }
        if (expression instanceof GreaterThan) {
            String left = ((GreaterThan) expression).getLeftExpression().toString();
            String right = ((GreaterThan) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (MetaData metaData : metaDataList) {
                Map map= metaData.getMetadata();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())>R){
                    newMetadata.add(metaData);
                }
            }


        }
        if (expression instanceof GreaterThanEquals) {
            String left = ((GreaterThanEquals) expression).getLeftExpression().toString();
            String right = ((GreaterThanEquals) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (MetaData metaData : metaDataList) {
                Map map= metaData.getMetadata();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())>=R){
                    newMetadata.add(metaData);
                }
            }

        }
        if (expression instanceof MinorThan) {
            String left = ((MinorThan) expression).getLeftExpression().toString();
            String right = ((MinorThan) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (MetaData metaData : metaDataList) {
                Map map= metaData.getMetadata();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())<R){
                    newMetadata.add(metaData);
                }
            }

        }
        if (expression instanceof MinorThanEquals) {
            String left = ((MinorThanEquals) expression).getLeftExpression().toString();
            String right = ((MinorThanEquals) expression).getRightExpression().toString();
            int R = Integer.parseInt(right);
            for (MetaData metaData : metaDataList) {
                Map map= metaData.getMetadata();
                Map map1=(Map) map.get("tableData");
                if(Integer.parseInt(map1.get(left).toString())<=R){
                    newMetadata.add(metaData);
                }
            }

        }
        return newMetadata;

    }

    /**
     * 开始解析insert语句
     *
     * @param insert Insert
     */
    private WebResult insertParser(Insert insert) {
        WebResult result = new WebResult();
        //表名
        String tableName = insert.getTable().getName();
        //字段名
        List<Column> colums = insert.getColumns();
        //字段值
        ExpressionList expressionList = (ExpressionList) insert.getItemsList();
        List<Expression> values = expressionList.getExpressions();

        Map map = toMap(colums, values);
        BigchainDBData data = new BigchainDBData(tableName, map);

        String id = null;
        try {
            id = bigchainDBUtil.createAsset(data);
            logger.info("插入操作成功！！！！！");
        } catch (Exception e) {
            logger.error("插入操作失败！！！！！");
            result.setData(null);
            result.setMessage("插入操作失败！！！！！");
            e.printStackTrace();
        }
        result.setCode(WebResult.CODE_SUCCESS);
        result.setMessage("insert");
        result.setData(id);
        return result;
    }

    /**
     * 开始解析update语句
     *
     * @param update Update
     */
    private WebResult updateParser(Update update) {
        WebResult result = new WebResult();
        //获得where后的资产ID数据
        EqualsTo expression = (EqualsTo) update.getWhere();
        String ID = expression.getLeftExpression().toString();
        String values = expression.getRightExpression().toString();
//        values = values.substring(1, values.length() - 1);

        if (!ID.equals("ID")) {
            logger.error("BDQL语法错误：where 只能使用ID=————，请检查书写和大小写");
            result.setMessage("BDQL语法错误：where 只能使用ID=————，请检查书写和大小写");
            result.setCode(WebResult.ERROR);
            result.setData(null);
            return result;
        }

        //表名
        String tableName = update.getTables().get(0).getName();

        //字段名
        List<Column> colums = update.getColumns();

        //字段值
        List<Expression> expressions = update.getExpressions();

        BigchainDBData data = new BigchainDBData(tableName, toMap(colums, expressions));
        String id = null;
        try {
            id = bigchainDBUtil.transferToSelf(data, values);
        } catch (Exception e) {
            logger.error("更新数据失败！！！！！！！");
            result.setCode(WebResult.ERROR);
            result.setData(null);
            result.setMessage("更新数据失败！！！！！！！");
            return result;
        }


        //TODO 插入完成后检查工作没有做

//        if (id == null) {
//            //TODO id为NULL
//        } else {
//            //TODO id不为null的时候，检查
//        }
        result.setCode(WebResult.CODE_SUCCESS);
        result.setMessage("更新数据成功！！！");
        result.setData(id);

        logger.info("更新数据成功！！！！");
        logger.info("交易ID：" + id);
        return result;
    }


    /**
     * 开始解析insert和update，就是同时插入assert和metadat
     *
     * @param BDQL
     * @return
     */
    private WebResult insertAndUpdateParser(String BDQL) {
        WebResult result = new WebResult();
        //TODO
        return result;
    }

    /**
     * 将两个list 合并到一个map中
     *
     * @param key
     * @param value
     * @return
     */
    private   Map toMap(List key, List value) {
        Map<String, String> map = new HashMap();
        for (int i = 0; i < key.size(); i++) {
            String sk = bdqlUtil.fixString(key.get(i).toString());
            String sv = bdqlUtil.fixString(value.get(i).toString());
            map.put(sk, sv);
        }
        return map;
    }

    /**
     * 获得语法错误原因
     *
     * @param e JSQLParserException
     * @return string cause by
     */
    private   String getJSQLParserExceptionCauseBy(JSQLParserException e) {
        String[] ss = e.getCause().getMessage().split(":");
        String s = ss[1].replace("\n", "");
        String result = s.replace("Was expecting one of", "");
        logger.error("BDQL语法错误原因，正在查找……………………");
        return result;
    }

    public static void main(String[] args) throws InterruptedException, IOException {


    }
}