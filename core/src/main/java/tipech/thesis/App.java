package tipech.thesis;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.IllegalStateException;
import javax.net.ssl.SSLException;


import tipech.thesis.entities.ControlMessage;
import tipech.thesis.entities.FeedGroup;
import tipech.thesis.entities.Feed;
import tipech.thesis.entities.FeedItem;

import tipech.thesis.extraction.RSSFeedParser;
import tipech.thesis.extraction.KeywordExtractor;


/**
 * 
 *
 */
public class App 
{

	public enum STATE {
		IDLE, KEYWORDS, LIVE
	}

	private static STATE state;

	private static BufferedReader bufferedReader;


	public static void main( String[] args )
	{
		try {

			// ---------- General configuration -----------
			long TIMEOUT = System.currentTimeMillis()+ 20*1000;
			state = STATE.IDLE;


			// ---------- Objects Initialization ----------
			bufferedReader = new BufferedReader(new InputStreamReader(System.in));

			DateTimeFormatter rssDateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
			LocalDate rejectDate = null;

			KeywordExtractor keywordExtractor = new KeywordExtractor();


			// --------- Internal Loop variables ----------
			boolean done = false;
			
			List<FeedGroup> groupsList = new ArrayList<FeedGroup>();
			int groupIndex = 0;
			int feedIndex = 0;
			int messageCount = 0;
			String feedUrl;


			// ============ Main Loop ===========
			while ( System.currentTimeMillis() < TIMEOUT && !done ) {

				switch (state){

					// ------------ Idle State ------------
					case IDLE:
						ControlMessage message = new ControlMessage(checkInput(true));

						// Wait for start command
						if( message.getCommand().equals("start") ){

							groupsList = message.getGroups();
							rejectDate = message.getRejectDate();

							state = STATE.KEYWORDS;
							groupIndex = 0;
							feedIndex = 0;
							System.out.println("Start command received, Fetching RSS...");
						}

						break;

					// ----- Keyword Extraction State -----
					case KEYWORDS:

						checkInput(false);
						final LocalDate filterDate = rejectDate;

						// Fetch a single feed from a single group
						feedUrl = groupsList.get(groupIndex).getFeeds().get(feedIndex);
						System.out.println("Fetching RSS and extracting keywords from: "+ feedUrl);
						try{

							// RSS fetching
							RSSFeedParser parser = new RSSFeedParser(feedUrl);
							Feed feed = parser.readFeed();
							List<FeedItem> feedItems = feed.getEntries();

							feedItems.stream()
								// Filter out too old
								.filter( headline ->
									LocalDate.parse(headline.getPubDate(), rssDateFormat).isAfter(filterDate)
								)
								// Extract keywords
								.map( headline ->
									keywordExtractor
										.extract( headline.getTitle() +"\n"+ headline.getDescription() )
										.entrySet()
								)
								// Filter out too small
								.filter( keywordSet ->
									keywordSet.stream()
										.reduce(
											0,
											(sum, word) -> sum + word.getValue().get(1),
											(sum1, sum2) -> sum1 + sum2
										) > 2
								)
								.forEach( keywordSet -> System.out.println( keywordSet ) );

							
						} catch(SSLException e){
							System.out.println("RSS over https connection not supported!");
						}

						// Move on to the next feed/group
						if (feedIndex < groupsList.get(groupIndex).getFeeds().size()-1){

							feedIndex++;
						} else if (groupIndex < groupsList.size()-1){

							groupIndex++;
							feedIndex = 0;
						} else {
							// Keyword extraction done
							// System.out.println(messageCount);
							state = STATE.LIVE;
						}						
						break;

					// ------- Live Streaming State -------
					case LIVE:
						done = true;
						break;
				}
			}



		// ==== Termination & Error Handling ====

		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bufferedReader != null) {
					bufferedReader.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static String checkInput(boolean block) throws IOException{

		if( block || (!block && bufferedReader.ready()) ){ // if blocking, or stdin not empty

			return bufferedReader.readLine();
		} else {
			return null;
		}
	}
}