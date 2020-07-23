package com.keer.bdql.Controller;


import com.bigchaindb.api.OutputsApi;
import com.bigchaindb.model.Output;
import com.bigchaindb.model.Outputs;
import com.keer.bdql.BDQLParser.BDQLUtil;
import com.keer.bdql.Bigchaindb.BigChainDBUtilByMongo;
import com.keer.bdql.Bigchaindb.BigchainDBRunner;
import com.keer.bdql.Bigchaindb.BigchainDBUtil;
import com.keer.bdql.Bigchaindb.KeyPairHolder;
import com.keer.bdql.Domain.WebResult;
import com.keer.bdql.Domain.mongo.Transactions;
import com.keer.bdql.Service.Implement.WebServiceImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class WebController {
    private static Logger logger = LoggerFactory.getLogger(WebController.class);
    @Autowired
    WebServiceImp bigchainDBServiceImp;

    @Autowired
    BigChainDBUtilByMongo bigChainDBUtilByMongo;

    @Autowired
    KeyPairHolder keyPairHolder;

    /**
     * 获取秘钥
     *
     * @return
     */
    @GetMapping("/getKey/{key}")
    public WebResult getKey(@PathVariable String key) {
        logger.info("获取秘钥");
        return bigchainDBServiceImp.getKey(key);
    }

    /**
     * 连接BigchainDB节点
     *
     * @param map
     * @return
     */
    @PostMapping("/startConn")
    public WebResult startConn(@RequestBody Map map) {
        String ip = map.get("ip").toString();
        return bigchainDBServiceImp.startConn(ip);
    }

    /***
     * 获取数据中所有表名，组成jstree的数据格式
     * @param map
     * @return
     */
    @PostMapping("/getCloumns")
    public WebResult getCloumnsName(@RequestBody Map map) {
        String key = map.get("key").toString();
        return bigchainDBServiceImp.getCloumnsName(key);
    }

    /**
     * 获得相应表的数据
     *
     * @param name
     * @param operation
     * @return
     */
    @RequestMapping(value = "/getTableData/{name}/{operation}", method = RequestMethod.GET)
    public WebResult getTableData(@PathVariable String name, @PathVariable String operation) {
        return bigchainDBServiceImp.getTableData(name, operation);
    }


    @PostMapping("/runBDQL")
    public WebResult runBDQL(@RequestBody Map map) {
        String BDQL = map.get("bdql").toString();
        return bigchainDBServiceImp.runBDQL(BDQL);
    }

    @GetMapping("/test")
    public Object test() {
        String pubkey=keyPairHolder.pubKeyToString(keyPairHolder.getPublic());
        return bigChainDBUtilByMongo.queryAsset(pubkey);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        WebResult result = new WebResult();
        BigchainDBUtil bigchainDBUtil = new BigchainDBUtil();
        BigchainDBRunner bigchainDBRunner = new BigchainDBRunner();
        KeyPairHolder keyPairHolder = new KeyPairHolder();
        BDQLUtil bdqlUtil = new BDQLUtil();
        bigchainDBRunner.StartConn();
        for (int j = 0; j < 10; j++) {
            result = bdqlUtil.work("INSERT INTO Computer (id, ip,mac,size,cpu,ROM,RAM) VALUES ('" + (j + 1) + "','" + (j + 2) + "','Champs-Elysees','" + (j + 3) + "','i7','" + (j + 4) + "','" + (j + 5) + "')");
            String id = (String) result.getData();
            logger.info("资产ID：" + id);

            logger.info(bigchainDBUtil.checkTransactionExit(id) + "");
            WebResult result1 = new WebResult();
            for (int i = 0; i < 10; i++) {
                result1 = bdqlUtil.work("UPDATE Person SET FirstName = '" + i + "' , SecondName='" + j + "',age= '" + (i + j) + "',time='" + (i + j + 10) + "' WHERE ID='" + id + "'");
                logger.info("交易ID：" + result1.getData());
                Thread.sleep(500);

            }
        }

        Outputs outputs = OutputsApi.getOutputs(keyPairHolder.pubKeyToString(keyPairHolder.getPublic()));
        logger.info("交易总数1：" + outputs.getOutput().size());
        for (Output output : outputs.getOutput()) {
            logger.info("交易ID：" + output.getTransactionId() + ",密钥：" + output.getPublicKeys());
        }

    }

}