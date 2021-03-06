package com.lanwei.haq.comm.thread;

import com.lanwei.haq.comm.util.EsUtil;
import com.lanwei.haq.comm.util.RedisUtil;
import com.lanwei.haq.comm.util.SpiderUtil;
import com.lanwei.haq.spider.dao.web.MysqlDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 爬虫资源管理：定时器、线程池、数据buffer Created by Carlisle on 2017/7/10.
 */
@Component
public class ResourceManager {

    @Autowired
    private MysqlDao mysqlDao;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private SpiderUtil spiderUtil;
    @Autowired
    private EsUtil esUtil;

    private static final int MAX_QUEUE_SIZE = 2000;

    private static Map<Integer, Timer> timers = new HashMap<>();
    private static Map<Integer, ResourceUnit> resource = new HashMap<>();

    private static Timer monitorTimer = new Timer();
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    public static void init() throws InterruptedException {
        monitorTimer.scheduleAtFixedRate(new ResourceMonitor(), 0, 10000);
    }

    public boolean setWebSpiderThreadAndStart(final int webId, int tnum, int cronTime) {
        logger.info("Receive request to start spider for website:" + webId + ",tnum:" + tnum);
        ResourceUnit unit = null;
        Timer t = null;
        //如果传入的网站id爬虫从未运行过，给该网站id分配资源，并立即开始执行
        if (!resource.containsKey(webId)) {
            logger.info("Website:" + webId + " is new, start to allocate resource...");
            unit = new ResourceUnit(webId, tnum);
            resource.put(webId, unit);
            logger.info("Website:" + webId + "resource allocated.");
        } else {//如果传入的网站id爬虫正在执行，获取其资源，并立即执行
            logger.info("Website:" + webId + " is running, start to refresh...");
            unit = resource.get(webId);
            t = timers.get(webId);
            //取消当前调度
            t.cancel();
            t = null;
        }
        t = new Timer();
        timers.put(webId, t);
        t.scheduleAtFixedRate(new SpiderTimer(mysqlDao.getWebById(webId),unit, redisUtil, spiderUtil, esUtil), 0, cronTime * 1000 * 60);
        logger.info("Website id:" + webId + "pider task scheduled at rate of " + cronTime + "s.");
        return true;

    }

    //用于检查各队列容量，以动态调整线程数量
    private static class ResourceMonitor extends TimerTask {

        ResourceMonitor() {

        }

        @Override
        public void run() {

            logger.info("============= ResourceManager:Queue Monitor ================");
            logger.info("     website       queue size      active thread");
            for (Entry<Integer, ResourceUnit> e : resource.entrySet()) {
                int webId = e.getKey();
                ResourceUnit unit = e.getValue();
                logger.info("        " + webId + "              " + unit.q.size() + "                 " + unit.threadpool.getActiveCount());
                if (unit.q.size() > MAX_QUEUE_SIZE) {
                    //do something
                }
            }
            logger.info("============================================================");

        }
    }

}
