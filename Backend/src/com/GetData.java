package com;

import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;

import com.bloomberglp.blpapi.Datetime;
import com.bloomberglp.blpapi.Event;
import com.bloomberglp.blpapi.Element;
import com.bloomberglp.blpapi.InvalidRequestException;
import com.bloomberglp.blpapi.Logging;
import com.bloomberglp.blpapi.Message;
import com.bloomberglp.blpapi.MessageIterator;
import com.bloomberglp.blpapi.Name;
import com.bloomberglp.blpapi.Request;
import com.bloomberglp.blpapi.Service;
import com.bloomberglp.blpapi.Session;
import com.bloomberglp.blpapi.SessionOptions;	

@SuppressWarnings("unused")
public class GetData {
	private static final Name SECURITY_DATA = new Name("securityData");
    private static final Name SECURITY = new Name("security");
    private static final Name FIELD_DATA = new Name("fieldData");
    private static final Name RESPONSE_ERROR = new Name("responseError");
    private static final Name SECURITY_ERROR = new Name("securityError");
    private static final Name FIELD_EXCEPTIONS = new Name("fieldExceptions");
    private static final Name FIELD_ID = new Name("fieldId");
    private static final Name ERROR_INFO = new Name("errorInfo");
    private static final Name CATEGORY = new Name("category");
    private static final Name MESSAGE = new Name("message");
	
	private String IP_ADDRESS;
	private int PORT;
	private ArrayList<String> d_securities;
	private ArrayList<String> d_fields;
        
    public GetData() {
    	//boolean isCrypto = false;
        IP_ADDRESS = "10.8.8.1";
        PORT = 8194;
        d_securities = new ArrayList<String>();
        d_securities.add("AMD UN Equity"); //AMD
        d_securities.add("AAPL UW Equity"); //Apple
        d_securities.add("BLKB UW Equity"); //Blackbaud
        d_securities.add("INDU Index"); //DOW
        d_securities.add("FB UW Equity"); //Facebook
        d_securities.add("GOOGL UW Equity"); //Google
        d_securities.add("IBM US Equity"); //IBM
        d_securities.add("MSFT US Equity"); //Microsoft
        d_securities.add("QCOM UW Equity"); //Qualcomm
        d_fields = new ArrayList<String>();
        d_fields.add("DS002"); //Description
        d_fields.add("LOW"); //Prev to last price
        d_fields.add("HIGH"); //Last Stock Price
        //	d_fields.add("RQ941"); //Upfront Price Percent Change on Day - Realtime
        //d_fields.add("RQ539"); //Monthly Average Official Mean Price Real Time
        //d_fields.add("RQ580"); //Today's Last Yield
        //d_fields.add("CUR_EMPLOYEES"); //Current Employees
        
    }
        
	public static void main(String args[]) throws Exception {
		GetData app = new GetData();
		app.run();
	}

	private void run() throws Exception {
		 SessionOptions sessionOptions = new SessionOptions();
		 sessionOptions.setServerHost(IP_ADDRESS);
		 sessionOptions.setServerPort(PORT);

		 System.out.println("Connecting to " + IP_ADDRESS + ":" + PORT);
		 Session session = new Session(sessionOptions);
		 if(!session.start()) {
		 	System.err.println("Failed to start session.");
		 	return;
		 }
		 if(!session.openService("//blp/refdata")) {
		 	System.err.println("Failed to open //blp/refdata");
		 	return;
		 }
		 
		 try {
			 sendRefDataRequest(session);
		 } catch (InvalidRequestException e) {
			 e.printStackTrace();
		 }
		 
		 // wait for events from session
		 eventLoop(session);
		 
		 session.stop();
		 System.out.println("SUCCESS MOTHERFUCKER!");

	}
	
	private void eventLoop(Session session) throws Exception {
		boolean done = false;
		while(!done) {
			Event event = session.nextEvent();
			if(event.eventType() == Event.EventType.PARTIAL_RESPONSE) {
				System.out.println("Processing Partial Response");
				processResponseEvent(event);
			} else if(event.eventType() == Event.EventType.RESPONSE) {
				System.out.println("Processing Response");
				processResponseEvent(event);
				done = true;
			} else {
				MessageIterator msgIter = event.messageIterator();
				while(msgIter.hasNext()) {
					Message msg = msgIter.next();
					System.out.println(msg.asElement());
					if(event.eventType() == Event.EventType.SESSION_STATUS) {
						if(msg.messageType().equals("Session Terminated") || msg.messageType().equals("SessionStartupFailure")) {
							done = true;
						}
					}
				}
			}
		}
	}
	
	private void processResponseEvent(Event event) throws Exception {
		BufferedWriter writer;
		File file = new File("field_output.txt"); //Create file for field information to populate
		file.createNewFile();
		writer = new BufferedWriter(new FileWriter(file));
		writer.flush();
		MessageIterator msgIter = event.messageIterator();
		while(msgIter.hasNext()) {
			Message msg = msgIter.next();
			//System.out.println("LOOK AT ME, I'M THE MSG!!: "+msg);
			if(msg.hasElement(RESPONSE_ERROR)) {
				printErrorInfo("Request Failed: ", msg.getElement(RESPONSE_ERROR));
				continue;
			}
			
			Element securities = msg.getElement(SECURITY_DATA);
			int numSecurities = securities.numValues();
			System.out.println("Processing " + numSecurities + " securities:");
			for(int i = 0; i < numSecurities; ++i) {
				Element security = securities.getValueAsElement(i);
				String ticker = security.getElementAsString(SECURITY);
				//TEST IF TICKER = "XBTUSD Curncy" if so, change its field data to exclude stuff other than PX_LAST
				System.out.println("\nTicker: " + ticker);//WRITE TO FILE HERE
				//writer.write("\n<SECURITY>"+ticker+"</SECURITY>\n"); 
				if(security.hasElement(SECURITY_ERROR)) {
					printErrorInfo("\tSECURITY FAILED: ", security.getElement(SECURITY_ERROR));
					continue;
				}
				
				if(security.hasElement(FIELD_DATA)) {
					Element fields = security.getElement(FIELD_DATA);
					if(fields.numElements() > 0) {
						System.out.println("FIELD\t\tVALUE");
						System.out.println("-----\t\t-----");
						int numElements = fields.numElements();
						for(int j = 0; j < numElements; ++j) {
							Element field = fields.getElement(j);
							System.out.println(field.name() + "\t\t" + field.getValueAsString()); //WRITE TO FILE
							if(field.name().equals("DS002"))
								writer.write(field.getValueAsString());
							else if(field.name().equals("LOW"))
								writer.write(":"+field.getValueAsString());
							else
								writer.write(":"+field.getValueAsString());
							
						}writer.newLine();
					}
				}
				System.out.println("");
				Element fieldExceptions = security.getElement(FIELD_EXCEPTIONS);
				if(fieldExceptions.numValues() > 0) {
					System.out.println("FIELD\t\tEXCEPTION");
					System.out.println("-----\t\t---------");
					for(int k = 0; k < fieldExceptions.numValues(); ++k) {
						Element fieldException = fieldExceptions.getValueAsElement(k);
						printErrorInfo(fieldException.getElementAsString(FIELD_ID) + "\t\t", fieldException.getElement(ERROR_INFO));
					}
				}
			}
		}//End of while loop
		writer.close();
	}
	
	private void sendRefDataRequest(Session session) throws Exception {
		Service refDataService = session.getService("//blp/refdata");
		Request request = refDataService.createRequest("ReferenceDataRequest");
		//Request request_historical = refDataService.createRequest("HistoricalDataRequest");
		
		//add securities to request
		Element securities = request.getElement("securities");
		//Element hist_securities = request_historical.getElement("securities");
		for(String security : d_securities) {
			securities.appendValue(security);
			//hist_securities.appendValue(security);
		}
		
		//add securities to request
		Element fields = request.getElement("fields");
		//Element hist_fields = request_historical.getElement("fields");
		for(String field : d_fields) {
			fields.appendValue(field);
			//hist_fields.appendValue(field);
		}
		
//		request_historical.set("periodicityAdjustment", "ACTUAL");
//        request_historical.set("periodicitySelection", "DAILY");
//        request_historical.set("startDate", "20141016"); //year/month/day year/mm/dd
//        request_historical.set("endDate", "20141017");
//        request_historical.set("maxDataPoints", 100);
//        request_historical.set("returnEids", true);
		System.out.println("Sending Request: " + request);
		session.sendRequest(request, null);
		//session.sendRequest(request_historical, null);
	}
	private void printErrorInfo(String leadingStr, Element errorInfo) throws Exception {
        System.out.println(leadingStr + errorInfo.getElementAsString(CATEGORY) +
                           " (" + errorInfo.getElementAsString(MESSAGE) + ")");
    }

}