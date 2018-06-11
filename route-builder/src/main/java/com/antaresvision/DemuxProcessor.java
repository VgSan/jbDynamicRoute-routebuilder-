package com.antaresvision;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Optional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.model.config.Channels;

public class DemuxProcessor implements Processor {

	public static final Logger logger = LoggerFactory.getLogger(DemuxProcessor.class);

	public void process(Exchange exchange) throws Exception {

		String configFilePath = (String) exchange.getIn().getHeader("CONFIGFilePath");
		String channelType = (String) exchange.getIn().getHeader("ChannelType");

		String body = exchange.getIn().getBody(String.class);
		Document xmlDocument = loadXMLFromString(body);

		JAXBContext jc = JAXBContext.newInstance(Channels.class);
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		File xml = new File(configFilePath);
		Channels input = (Channels) unmarshaller.unmarshal(xml);

		Optional<Channels.Channel> optionalChannel = input.getChannel().stream()
				.filter(c -> c.getType().equals(channelType)).findFirst();

		if (optionalChannel.isPresent()) {
			Channels.Channel channel = optionalChannel.get();
			Channels.Channel.Credentials credential = channel.getCredentials();

			List<Channels.Channel.To> toList = channel.getTo();
			for (int x = 0; x < toList.size(); x++) {
				Channels.Channel.To to = toList.get(x);

				XPath xPath = XPathFactory.newInstance().newXPath();
				String expression = to.getXpath();
				
				// Dev ref : http://www.baeldung.com/java-xpath
				Node node = (Node) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODE);
				logger.warn("XPATH node : " + node);

				if(node != null) {
					String toUri = credential.getUrl() + to.getDestinationFolder() + 
							"?username=" + credential.getUsername() + 
							"&password=" + credential.getPassword() + 
							"&fileName=${file:onlyname.noext.single}_${date:now:yyyyMMddHHmmssSSS}.${file:name.ext.single}";
					
					exchange.getIn().setHeader("destinationFolder", toUri);
					logger.info("Set header : " + toUri);
				}
			}
		}
	}

	public Document loadXMLFromString(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		byte[] byt = xml.getBytes();
		ByteArrayInputStream bytAryIptStrm = new ByteArrayInputStream(byt);
		return builder.parse(bytAryIptStrm);
	}
}
