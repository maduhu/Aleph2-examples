/*******************************************************************************
* Copyright 2015, The IKANOW Open Source Project.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package com.ikanow.aleph2.storm.samples.bolts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import com.ikanow.aleph2.storm.samples.script.CompiledScriptFactory;
import com.ikanow.aleph2.storm.samples.script.NoSecurityManager;
import com.ikanow.aleph2.storm.samples.script.PropertyBasedScriptProvider;

public class JavaScriptFolderBolt extends BaseRichBolt {
	private static final Logger logger = LogManager.getLogger(JavaScriptFolderBolt.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = -17206092588932701L;
	private OutputCollector _collector;	
	protected transient CompiledScriptFactory compiledScriptFactory = null;
	private String propertyFileName;
	
	protected static String FOLD_CALL = "fold(mapKey,mapValueJson);";
	protected static String CHECKEMIT_CALL = "checkEmit(mapKey,mapValueJson);";
	
	
	public JavaScriptFolderBolt(String propertyFileName){		
		
		this.propertyFileName  = propertyFileName;
		
	}
	
	protected CompiledScriptFactory getCompiledScriptFactory(){
		if(compiledScriptFactory == null){
			this.compiledScriptFactory = new CompiledScriptFactory(new PropertyBasedScriptProvider(propertyFileName){
				@Override
				public void init(String propertyFile) {
					super.init(propertyFile);
					scriptlets.add(FOLD_CALL);
					scriptlets.add(CHECKEMIT_CALL);
				}
				
			}, new NoSecurityManager());

			compiledScriptFactory.executeCompiledScript(CompiledScriptFactory.GLOBAL);			
		}
		return compiledScriptFactory;
	}
	
	@Override
	@SuppressWarnings("rawtypes")
	public void execute(Tuple tuple) {
		
		Long timer = null;
		try{
			timer = tuple.getLongByField("timer");

		}catch(Throwable t){}
		
		if(timer!=null){
			
		}else{
			// must be a message from the mapper
		
		String val0 = tuple.getString(0);
		logger.debug("JavaScriptBolt Received tuple:"+tuple+" val0:"+val0);
		LinkedHashMap<String, Object> tupelMap =tupleToLinkedHashMap(tuple);
		String mapKey = (String) tupelMap.get("mapKey");
		String mapValueJson = (String) tupelMap.get("mapValueJson");
		if(mapKey!=null && mapValueJson!=null){
			Object retVal = getCompiledScriptFactory().executeCompiledScript(FOLD_CALL,"mapKey",mapKey,"mapValueJson",mapValueJson);
			logger.debug("JavaScriptBolt Result from Script:"+retVal);
/*			if(retVal instanceof Map){
				Map m = (Map)retVal;
				String mapKey = (String)m.get("mapKey");
				String mapValueJson = (String)m.get("mapValueJson");
				if(mapKey!=null && mapValueJson!=null){
*/				
			Object checkEmit = getCompiledScriptFactory().executeCompiledScript(CHECKEMIT_CALL,"mapKey",mapKey,"mapValueJson",mapValueJson);
			 if(checkEmit!=null){
					_collector.emit(tuple, new Values(checkEmit));					 			 
			}
		}
		//always ack the tuple to acknowledge we've processed it, otherwise a fail message will be reported back
		//to the spout
		} // else
		_collector.ack(tuple);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		_collector = collector;
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("foldValue"));
	}
	
	public static LinkedHashMap<String, Object> tupleToLinkedHashMap(final Tuple t) {
		return StreamSupport.stream(t.getFields().spliterator(), false)
							.collect(Collectors.toMap(f -> f, f -> t.getValueByField(f), (m1, m2) -> m1, LinkedHashMap::new));
	}
	
/* public static void emit(Tuple t, OutputCollector collector,Object out){
		collector.emit(t, new Values(out));
 }*/
}