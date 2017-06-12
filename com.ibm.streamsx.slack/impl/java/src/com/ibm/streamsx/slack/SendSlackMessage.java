//
// ****************************************************************************
// * Copyright (C) 2017, International Business Machines Corporation          *
// * All rights reserved.                                                     *
// ****************************************************************************
//

package com.ibm.streamsx.slack;


import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;

import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.model.DefaultAttribute;
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
			description="Port that ingests tuples", 
			cardinality=1, 
			optional=false, 
			windowingMode=WindowMode.NonWindowed, 
			windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)})
@Libraries({
	// Include javax.mail libraries.
	"opt/downloaded/*"
	})
public class SendSlackMessage extends TupleConsumer {
	
	// ------------------------------------------------------------------------
	// Documentation.
	// Attention: To add a newline, use \\n instead of \n.
	// ------------------------------------------------------------------------

	static final String DESC_OPERATOR = 
			"The SendSlackMessage operator outputs the contents of the messageAttr attribute from "
		  + "incoming tuples to the Slack WebHook URL specified in the parameters."
		  + "\\n"
		  + "Custom usernames and icons can be used, instead of the default ones, through the "
		  + "usernameAttr and iconUrlAttr attributes."
		  + "\\n";
	
	@Parameter(
			optional=false,
			description="Specifies the Slack incoming WebHook URL to send messages to."
			)
	public void setSlackUrl(String slackUrl) throws IOException {
		this.slackUrl = slackUrl;
	}
	
	@Parameter(
			optional=true,
			description="Incoming tuple attribute that specifies the username for the slack message. "
					  + "The default username is specified in the incoming WebHook's configuration."
			)
	public void setUsernameAttr(TupleAttribute<Tuple, String> usernameAttr) throws IOException {
		this.usernameAttr = usernameAttr;
	}
	
	@Parameter(
			optional=true,
			description="Incoming tuple attribute that specifies the icon URL for the slack message. "
					  + "The default icon is specified in the incoming WebHook's configuration."
			)
	public void setIconUrlAttr(TupleAttribute<Tuple, String> iconUrlAttr) throws IOException {
		this.iconUrlAttr = iconUrlAttr;
	}
	
	@DefaultAttribute("message")
	@Parameter(
			optional=true,
			description="Incoming tuple attribute to use as content for the slack message. "
					  + "The default attribute to use is 'message'."
			)
	public void setMessageAttr(TupleAttribute<Tuple, String> messageAttr) throws IOException {
		this.messageAttr = messageAttr;
	}
	
	// ------------------------------------------------------------------------
	// Implementation.
	// ------------------------------------------------------------------------
	
	/**
	 * Logger for tracing.
	 */
	private static Logger _trace = Logger.getLogger(SendSlackMessage.class.getName());
	
	/**
	 * Slack incoming WebHook URL.
	 */
	private String slackUrl;
	
	/**
	 * Attribute containing username to use for message.
	 */
	private TupleAttribute<Tuple, String> usernameAttr;
	
	/**
	 * Attribute containing icon URL to use for message.
	 */
	private TupleAttribute<Tuple, String> iconUrlAttr;
	
	/**
	 * Attribute containing message to send.
	 */
	private TupleAttribute<Tuple, String> messageAttr;
	
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
	
        httpclient = HttpClients.custom().setConnectionTimeToLive(1000, TimeUnit.MILLISECONDS).setMaxConnPerRoute(1000).build();
        
        // Connect POST to Slack WebHook URL.
		httppost = new HttpPost(slackUrl);
		httppost.addHeader("Content-type", "application/json");
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
    	
    	// Send Slack message if slack webhook URL is specified.
		if (slackUrl != null) {
			
			// Message to post on slack channel.
	    	String message = messageAttr.getValue(tuple);
	    	
			JSONObject json = new JSONObject();
			json.put("text", message);
			
			// Override WebHook username and icon, if params defined.
			if (usernameAttr != null) {
				json.put("username", usernameAttr.getValue(tuple));
			}
			if (iconUrlAttr != null) {
				json.put("icon_url", iconUrlAttr.getValue(tuple));
			}
			
			StringEntity params = new StringEntity(json.toString(), "UTF-8");
			params.setContentType("application/json");
			httppost.setEntity(params);
			
			// Attempt to send message.
			HttpResponse response = httpclient.execute(httppost);
			int responseCode = response.getStatusLine().getStatusCode();
			
			// Send successful - remove message from batch queue.
			if (responseCode == 200) {
				batch.remove();
				
				// Can only send 1 message to Slack, per second.
				Thread.sleep(1000);
			} else {
				_trace.error(responseCode + response.toString());
			}
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
}

