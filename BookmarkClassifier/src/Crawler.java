import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;

public class Crawler {
	public LinkedHashSet weblinkList;

	public static void main(String[] args) {
		ArrayList<Integer> bookmarkIdList = new ArrayList<Integer>();
		ArrayList<String> urlList = new ArrayList<String>();
		bookmarkIdList.add(0);
		urlList.add("https://www.google.com/");
		
		bookmarkIdList.add(1);
		urlList.add("http://www.yahoo.com/");
		
		HashMap<Integer, HashMap<String, String>> bookmarkResultMap = CrawlBookmarks(bookmarkIdList, urlList);
		WriteXMLFile.WriteHTMLDocumentToXML(bookmarkResultMap, bookmarkIdList);
	}

	public static HashMap<Integer, HashMap<String, String>> CrawlBookmarks(
			ArrayList<Integer> bookmarkIdList, HashMap<Integer,String> urlMap) {
		HashMap<Integer, HashMap<String, String>> bookmarkResultMap = new HashMap<Integer, HashMap<String, String>>();
		for (Integer id : bookmarkIdList) {

			try {

				String pageURL = urlMap.get(id);
				Crawler crawler = new Crawler();
				String pageContents = crawler.Crawl(pageURL);

				System.out.println("weblinkList:" + crawler.weblinkList.size());
				Object arr[] = crawler.weblinkList.toArray();

				for (int i = 0; i < crawler.weblinkList.size(); i++) {
					System.out.println("weblinkList (" + i + "):" + arr[i]);
				}

				System.out.println("pageContents:" + pageContents);
				HashMap<String, String> htmlContentMap = HTMLPreProcessor
						.TokenizeHTMLDocument(pageContents);
				htmlContentMap.put(XMLConstants.XML_ATTR_ID, ""+id);
				bookmarkResultMap.put(id, htmlContentMap);

				String htmlBody = htmlContentMap
						.remove(HTMLConstants.HTML_BODY);
				System.out.println("Title:"
						+ htmlContentMap.get(HTMLConstants.HTML_TITLE));
				System.out.println("Body:" + htmlBody);
				String pageContentsWithoutScripts = HTMLPreProcessor
						.RemoveScriptFromHTML(htmlBody);
				String pageContentsWithoutHTMLelements = HTMLPreProcessor
						.ReplaceHTMLElementsWithText(pageContentsWithoutScripts);
				String pageContentsWithoutHyperlink = HTMLPreProcessor
						.RemoveHyperlinksFromHTML(
								pageContentsWithoutHTMLelements, pageURL);
				htmlContentMap.put(HTMLConstants.HTML_BODY,
						pageContentsWithoutHyperlink);

				System.out.println("pageContentsWithoutScripts:"
						+ pageContentsWithoutScripts);
				System.out.println("pageContentsWithoutHTMLelements:"
						+ pageContentsWithoutHTMLelements);
				System.out
						.println("Plain Text:" + pageContentsWithoutHyperlink);

				System.out.println("\n\nContent Type\tContent Length");
				System.out.println("Raw:\t\t" + pageContents.length());
				System.out.println("Body:\t\t" + htmlBody.length());
				System.out.println("Without Script:\t"
						+ pageContentsWithoutScripts.length());
				System.out.println("Without HTML:\t"
						+ pageContentsWithoutHTMLelements.length());
				System.out.println("Plain Text:\t"
						+ pageContentsWithoutHyperlink.length());

			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return bookmarkResultMap;
	}

	public String Crawl(String startUrl) {

		String pageContents = "";
		// Set up crawl lists.
		HashSet crawledList = new HashSet();
		LinkedHashSet toCrawlList = new LinkedHashSet();
		// Add start URL to the To Crawl list.
		toCrawlList.add(startUrl);

		// Get URL at bottom of the list.
		String url = (String) toCrawlList.iterator().next();
		// Remove URL from the To Crawl list.
		toCrawlList.remove(url);
		// Convert string url to URL object.
		URL verifiedUrl = verifyUrl(url);

		// Add page to the crawled list.
		crawledList.add(url);
		// Download the page at the given URL.
		pageContents = downloadPage(verifiedUrl);

		if (pageContents != null && pageContents.length() > 0) {
			// Retrieve list of valid links from page.
			ArrayList links = retrieveLinks(verifiedUrl, pageContents,
					crawledList);
			// Add links to the To Crawl list.
			toCrawlList.addAll(links);
		}
		weblinkList = toCrawlList;
		return pageContents;
	}

	private static String downloadPage(URL pageUrl) {
		// System.out.println("Inside downloadPage");
		try {
			// Open connection to URL for reading.
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					pageUrl.openStream()));
			// Read page into buffer.
			String line;
			StringBuffer pageBuffer = new StringBuffer();
			while ((line = reader.readLine()) != null) {
				pageBuffer.append("\n" + line);
			}
			return pageBuffer.toString();
		} catch (Exception e) {
		}
		return null;
	}

	private static URL verifyUrl(String url) {
		if (!url.toLowerCase().startsWith("http://")) {
			return null;
		}
		URL verifiedUrl = null;
		try {
			verifiedUrl = new URL(url);
		} catch (Exception e) {
			return null;
		}
		return verifiedUrl;
	}

	private static ArrayList retrieveLinks(URL pageUrl, String pageContents,
			HashSet crawledList) {
		// System.out.println("Inside Retrieve Links");
		Pattern p = Pattern.compile("<a\\s+href\\s*=\\s*\"?(.*?)[\"|>]",
				Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(pageContents);
		// Create list of link matches.
		ArrayList linkList = new ArrayList();
		while (m.find()) {
			String link = m.group(1).trim();
			// Skip empty links.
			if (link.length() < 1) {
				continue;
			}
			// Skip links that are just page anchors.
			if (link.charAt(0) == '#') {
				continue;
			}
			// Skip mailto links.
			if (link.indexOf("mailto:") != -1) {
				continue;
			}
			// Skip Javascript links.
			if (link.toLowerCase().indexOf("javascript") != -1) {
				continue;
			}
			// Prefix absolute and relative URLs if necessary.
			if (link.indexOf("://") == -1) {
				// Handle absolute URLs.
				if (link.charAt(0) == '/') {
					link = "http://" + pageUrl.getHost() + link;
					// Handle relative URLs.
				} else {
					String file = pageUrl.getFile();
					if (file.indexOf('/') == -1) {
						link = "http://" + pageUrl.getHost() + "/" + link;
					} else {
						String path = file.substring(0,
								file.lastIndexOf('/') + 1);
						link = "http://" + pageUrl.getHost() + path + link;
					}
				}
			}
			// Remove anchors from link.
			int index = link.indexOf('#');
			if (index != -1) {
				link = link.substring(0, index);
			}
			// Remove leading "www" from URL's host if present.
			link = removeWwwFromUrl(link);
			// Verify link and skip if invalid.
			URL verifiedLink = verifyUrl(link);
			if (verifiedLink == null) {
				continue;
			}
			// Skip link if it has already been crawled.
			if (crawledList.contains(link)) {
				continue;
			}
			// Add link to list.
			linkList.add(link);
			// System.out.println("\n\n Link:" + link.toString());
		}
		return (linkList);
	}

	private static String removeWwwFromUrl(String url) {
		// System.out.println("Inside RemoveWwwFromUrl");
		int index = url.indexOf("://www.");
		if (index != -1) {
			return url.substring(0, index + 3) + url.substring(index + 7);
		}
		return (url);
	}
}
