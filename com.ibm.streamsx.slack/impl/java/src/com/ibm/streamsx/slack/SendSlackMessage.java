//
// ****************************************************************************
// * Copyright (C) 2017,2018 International Business Machines Corporation      *                                                     *
// ****************************************************************************
//

package com.ibm.streamsx.slack;


import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.Type;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.samples.patterns.TupleConsumer;

@PrimitiveOperator(
		name="SendSlackMessage", 
		namespace="com.ibm.streamsx.slack",
		description=SendSlackMessage.DESC_OPERATOR
		)
@InputPorts({
	@InputPortSet(
          description="Each tuple is converted to JSON and sent as a message to the "
            + "Slack incoming web hook.\\n\\n"
            + "The port's schema must be one of:\\n"
            + " * [Json] - Streams standard JSON schema. Each tuple represents a JSON object and is used as the message contents. This allows the application to use any field supported by the Slack web hook.\\n"
            + " * A single `rstring` or `ustring` attribute (which includes the default string exchange schema [String]). The value of the string is used as the `text` property in the JSON message object.\\n"
            + " * A schema containing an attribute named `text` with type `rstring` or `ustring`. The value of the `text` attributed is used as the `text` property in the JSON message object. [Message] type may be used to augment other schemas.\\n",

			cardinality=1, 
			optional=false, 
			windowingMode=WindowMode.NonWindowed, 
			windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)})
@Libraries({
	"opt/downloaded/*"
	})
public class SendSlackMessage extends TupleConsumer {
	
	// ------------------------------------------------------------------------
	// Documentation.
	// Attention: To add a newline, use \\n instead of \n.
	// ------------------------------------------------------------------------

	protected static final String DESC_OPERATOR = 
			"Sends messages to a Slack incoming web hook."
		  + "\\n"
		  + "Each incoming tuple results in a message sent to the incoming web hook."
		  + "The tuple is converted to JSON and sent to the incoming web hook."
                  + "\\n\\n"
                  + "This operator forms the basis for the [SlackMessageService] microservice."
                  + "\\n\\n"
                  + "Guide on Slack incoming web hooks: [https://api.slack.com/incoming-webhooks]\\n"


        ;
	
	@ContextCheck(runtime=false)
	public static void validateSchema(OperatorContextChecker checker) {
	    
	    StreamingInput<?> port = checker.getOperatorContext().getStreamingInputs().get(0);
	    StreamSchema schema = port.getStreamSchema();
	    
	    if (isJson(schema))
	        return;
	    
	    if (isStringAttribute(schema))
	        return;
	    
	    if (!checker.checkRequiredAttributes(port, "text"))
	        return;
	    checker.checkAttributeType(schema.getAttribute("text"),
	            Type.MetaType.RSTRING, Type.MetaType.USTRING);	    
	}

    /**
     * Return true if the schema is Json.
     */
    private static boolean isJson(StreamSchema schema) {
        if (schema.getAttributeCount() == 1) {
            Attribute attr = schema.getAttribute(0);
            if (attr.getType().getMetaType() == Type.MetaType.RSTRING) {
                return attr.getName().equals("jsonString");
            }
        }
        return false;
    }
    
	/**
	 * Return true if the schema contains a single string attribute
	 * (not named text).
	 */
	private static boolean isStringAttribute(StreamSchema schema) {
        if (schema.getAttributeCount() == 1) {
            Attribute attr = schema.getAttribute(0);
            if (attr.getType().getMetaType() == Type.MetaType.RSTRING
                || attr.getType().getMetaType() == Type.MetaType.USTRING) {
                if (!attr.getName().equals("text")) {
                    return true;
                }
            }
        }
        return false;
	}
	
	@Parameter(
			optional=true,
			description="Specifies the Slack incoming web hook URL to send messages to."
			)
	public void setSlackUrl(String slackUrl) throws IOException {
		this.slackUrl = slackUrl;
	}
	
	@Parameter(
			optional=true,
			description="Name of application configuration containing operator parameters. The incoming web hook URL can be set in the application configuration by setting the `slackUrl` property. This is more flexible than the operator parameter as the incoming web hook URL can be changed while the application is running."
			)
	public void setSlackConfiguration(String applicationConfigurationName) throws IOException {
		if (!applicationConfigurationName.isEmpty()) {
			this.applicationConfigurationName = applicationConfigurationName;
		}
	}
	
	// ------------------------------------------------------------------------
	// Implementation.
	// ------------------------------------------------------------------------
	
	/**
	 * Logger for tracing.
	 */
	private static Logger _trace = Logger.getLogger(SendSlackMessage.class.getName());
	
	/**
	 * Application configuration key names.
	 */
	private static final String PARAM_SLACK_URL = "slackUrl";
	
	/**
	 * Slack incoming WebHook URL.
	 */
	private String slackUrl;
		
	/**
	 * Name of application configuration containing operator parameter values.
	 */
	private String applicationConfigurationName;
	
	private Map<String,String> applicationProperties;
	 
	/**
     * True if the schema is Json (rstring jsonString).
     * Value is used as-is for the request body.
     */
    private boolean json;
    
	/**
	 * True if the schema is a single string attribute
	 * used as the message text.
	 */
	private boolean singleString;
	
	private JSONEncoding<JSONObject, JSONArray> tuple2JSON;
	
	/**
	 * HTTP client and post.
	 */
	HttpClient httpclient;
	HttpPost httppost;
	
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
    	// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        fetchApplicationProperties();
        
        // Test we have a URL early
        updateSlackUrl();
	
        httpclient = HttpClients.custom().setConnectionTimeToLive(30, TimeUnit.SECONDS).setMaxConnPerRoute(1000).build();
        
        // Figure out how tuples are converted to the JSON message body
        // for the webhook.
        
        if (isJson(getInput(0).getStreamSchema()))
            json = true;
        else if (isStringAttribute(getInput(0).getStreamSchema()))
            singleString = true;
        else
            tuple2JSON = EncodingFactory.getJSONEncoding();
	}

    /**
     * Output message attribute from batched tuple to slack WebHook URL.
     * @param batch
     */
    @Override
    protected boolean processBatch(Queue<BatchedTuple> batch) throws Exception {
    	
    	// Get head tuple in batch.
    	BatchedTuple batchedTuple = batch.peek();
    	Tuple tuple = null;
    	if (batchedTuple != null) {
    		tuple = batchedTuple.getTuple();
    	} else {
    		return true;
    	}
    	
    	// Update slackUrl with the one defined in the application configuration.
    	updateSlackUrl();
    	
    	String msg;
    	
    	if (json) {
    	    // Use JSON value as-is
    	    msg = tuple.getString(0);
    	} else if (singleString) {
    	    // Single String attribute use as the text value.
    	    JSONObject obj = new JSONObject();
    	    obj.put("text", tuple.getString(0));
    	    msg = obj.toString();
    	} else {
    	    // Simply convert the full tuple to JSON and send that
    	    // as the message.
    	    msg = tuple2JSON.encodeAsString(tuple);
    	}
		
		StringEntity params = new StringEntity(msg, "UTF-8");
		params.setContentType("application/json");
		httppost.setEntity(params);
		
		// Attempt to send message.
		HttpResponse response = httpclient.execute(httppost);
		int responseCode = response.getStatusLine().getStatusCode();
		EntityUtils.consume(response.getEntity());
		
		// Send successful - remove message from batch queue.
		if (responseCode == HttpStatus.SC_OK) {
			batch.remove();
			
			// Can only send 1 message to Slack, per second.
			Thread.sleep(1000);
		} else {
	         _trace.error(responseCode + response.toString());
	          
	        // With a 404 maybe a URL has been updated in the application config.
		    if (responseCode == HttpStatus.SC_NOT_FOUND)
		        fetchApplicationProperties();
		}

		return true;
    }

    public void setBatchSize(int batchSize) { }

    @Override
    public synchronized void shutdown() throws Exception {
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

        // Must call super.shutdown()
        super.shutdown();
    }
    
    /**
     * Update slackUrl with the one defined in the application configuration.
     * @throws Exception
     */
    private void updateSlackUrl() throws Exception {
        if (applicationProperties.containsKey(PARAM_SLACK_URL)) {
            String applicationConfigurationSlackUrl = applicationProperties.get(PARAM_SLACK_URL);
            if (!applicationConfigurationSlackUrl.equals(slackUrl)) {
                slackUrl = applicationConfigurationSlackUrl;
                httppost = new HttpPost(slackUrl);
                httppost.addHeader("Content-type", "application/json");
            }
            return;
        }
    	
    	// Slack URL in application configuration not defined. Use slackUrl from operator parameters, instead.
    	if (slackUrl != null) {
			httppost = new HttpPost(slackUrl);
			httppost.addHeader("Content-type", "application/json");
		} else {
			throw new Exception(PARAM_SLACK_URL + " can't be found in application configuration or in the operator's parameters.");
		}
    }
    
	/**
	 * Calls the ProcessingElement.getApplicationConfiguration() method to
	 * retrieve the application configuration if application configuration
	 * is supported.
	 * 
	 * Called only at initialization or if a 404 is received.
	 */
	protected void fetchApplicationProperties() {
		
		if (applicationConfigurationName == null) {
			applicationProperties = Collections.emptyMap();
			return;
		}
		
		applicationProperties = getOperatorContext().getPE().getApplicationConfiguration(applicationConfigurationName);
	}
}

