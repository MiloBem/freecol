/**
 *  Copyright (C) 2002-2016   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.networking;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.io.FreeColXMLWriter.WriteScope;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.networking.DOMMessage;
import net.sf.freecol.common.util.Introspector;
import net.sf.freecol.server.control.ChangeSet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/**
 * Class for parsing raw message data into an XML-tree and for creating new
 * XML-trees.
 */
public class DOMMessage {

    protected static final Logger logger = Logger.getLogger(DOMMessage.class.getName());

    private static final String FREECOL_PROTOCOL_VERSION = "0.1.6";

    private static final String INVALID_MESSAGE = "invalid";

    private static DocumentBuilder builder = null, parser = null;
    static {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException pce) {
            // Never seen this in practice.  Apparently thrown if a
            // parser with specified options can not be built.
            logger.log(Level.WARNING, "Parser failure", pce);
        }
    };

    /** The actual message data. */
    protected Document document;


    /**
     * Constructs a new DOMMessage with data from the given InputStream. The
     * constructor to use if this is an INCOMING message.
     *
     * @param inputStream The <code>InputStream</code> to get the XML-data
     *            from.
     * @exception IOException if thrown by the <code>InputStream</code>.
     * @exception SAXException if thrown during parsing.
     */
    public DOMMessage(InputStream inputStream)
        throws SAXException, IOException {
        this.document = readDocument(new InputSource(inputStream));
    }

    /**
     * Create a DOMMessage with given tag and attributes.
     *
     * @param tag The main tag.
     * @param attributes Attribute,value pairs.
     */
    public DOMMessage(String tag, String... attributes) {
        this.document = createNewDocument();
        Element root = this.document.createElement(tag);
        this.document.appendChild(root);
        String[] all = attributes;
        for (int i = 0; i < all.length; i += 2) {
            root.setAttribute(all[i], all[i+1]);
        }
    }


    /**
     * Create a DOMMessage from an element.
     *
     * @param game The <code>Game</code> to create the message in.
     * @param element The <code>Element</code> to create the message from.
     * @return The message created, or null on failure.
     */
    public static DOMMessage createMessage(Game game, Element element) {
        if (element == null) return null;
        String tag = element.getTagName();
        tag = "net.sf.freecol.common.networking."
            + tag.substring(0, 1).toUpperCase() + tag.substring(1)
            + "Message";
        Class[] types = { Game.class, Element.class };
        Object[] params = { game, element };
        DOMMessage message;
        try {
            message = (DOMMessage)Introspector.instantiate(tag, types, params);
        } catch (Introspector.IntrospectorException ex) {
            logger.log(Level.WARNING, "Instantiation fail for message type:"
                + tag, ex);
            message = null;
        }
        return message;
    }

    /**
     * Gets the root element of the document.
     *
     * @return The root element.
     */
    private Element getElement() {
        return this.document.getDocumentElement();
    }
    
    /**
     * Gets the type of this DOMMessage.
     *
     * @return The type of this DOMMessage.
     */
    public String getType() {
        return (this.document != null && getElement() != null)
            ? getElement().getTagName()
            : INVALID_MESSAGE;
    }

    /**
     * Checks if this message is of a given type.
     *
     * @param type The type you wish to test against.
     * @return <code>true</code> if the type of this message equals the given
     *         type and <code>false</code> otherwise.
     */
    public boolean isType(String type) {
        return getType().equals(type);
    }

    /**
     * Gets an attribute from the root element.
     *
     * @param key The key of the attribute.
     * @return The value of the attribute with the given key.
     */
    public String getAttribute(String key) {
        return getElement().getAttribute(key);
    }

    /**
     * Sets an attribute on the root element.
     *
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    public void setAttribute(String key, String value) {
        getElement().setAttribute(key, value);
    }

    /**
     * Sets an attribute on the root element.
     *
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    public void setAttribute(String key, int value) {
        setAttribute(key, Integer.toString(value));
    }

    /**
     * Checks if an attribute is set on the root element.
     *
     * @param attribute The attribute in which to verify the existence of.
     * @return <code>true</code> if the root element has the given attribute.
     */
    public boolean hasAttribute(String attribute) {
        return getElement().hasAttribute(attribute);
    }

    public DOMMessage add(Element element) {
        getElement().appendChild(this.document.importNode(element, true));
        return this;
    }
    public DOMMessage add(FreeColObject fco) {
        add(toXMLElement(fco, this.document, (Player)null));
        return this;
    }
    public DOMMessage add(FreeColObject fco, Player player) {
        add(toXMLElement(fco, this.document, player));
        return this;
    }
    public DOMMessage add(DOMMessage msg) {
        add(msg.toXMLElement());
        return this;
    }
    public void clearChildren() {
        Element element = getElement();
        NodeList nl = element.getChildNodes();
        for (int i = nl.getLength() - 1; i >= 0; i--) {
            element.removeChild(nl.item(i));
        }
    }

    /**
     * Dummy serialization stub.
     * Must be overridden by subclasses.
     *
     * @return The document element.
     */
    public Element toXMLElement() {
        return getElement();
    }


    // Collection of static methods.
    // Much of the Element manipulation needs to go away.

    /**
     * Gets the current version of the FreeCol protocol.
     *
     * @return The version of the FreeCol protocol.
     */
    public static String getFreeColProtocolVersion() {
        return FREECOL_PROTOCOL_VERSION;
    }

    /**
     * Creates and returns a new XML-document.
     *
     * @return the new XML-document.
     */
    public static Document createNewDocument() {
        synchronized (builder) {
            return builder.newDocument();
        }
    }

    /**
     * Collapses a list of elements into a "multiple" element
     * with the original elements added as child nodes.
     *
     * @param elements A list of <code>Element</code>s to collapse.
     * @return A new "multiple" element, or the singleton element of the list,
     *     or null if the list is empty.
     */
    public static Element collapseElements(List<Element> elements) {
        switch (elements.size()) {
        case 0:
            return null;
        case 1:
            return elements.get(0);
        default:
            break;
        }
        Element first = elements.remove(0);
        Document doc = first.getOwnerDocument();
        Element result = doc.createElement("multiple");
        result.appendChild(first);
        for (Element e : elements) {
            result.appendChild(doc.importNode(e, true));
        }
        return result;
    }

    /**
     * Convenience method to find the first child element with the
     * specified tagname.
     *
     * @param element The <code>Element</code> to search for the child
     *     element in.
     * @param tagName The tag name of the child element to be found.
     * @return The first child element with the given name.
     */
    public static Element getChildElement(Element element, String tagName) {
        NodeList n = element.getChildNodes();
        for (int i = 0; i < n.getLength(); i++) {
            if (n.item(i) instanceof Element
                && ((Element)n.item(i)).getTagName().equals(tagName)) {
                return (Element)n.item(i);
            }
        }
        return null;
    }

    /**
     * Convenience method to extract a child element of a particular class.
     *
     * Special handling for null game, so that
     * <code>LoginMessage</code> can bootstrap itself despite no game
     * existing yet.
     *
     * @param game The <code>Game</code> to instantiate within.
     * @param element The parent <code>Element</code>.
     * @param index The index of the child element.
     * @param returnClass The expected class of the child.
     * @return A new instance of the return class, or null on error.
     */
    public static <T extends FreeColObject> T getChild(Game game,
        Element element, int index, Class<T> returnClass) {
        T ret = null;
        NodeList nl = element.getChildNodes();
        Element e;
        if (index < nl.getLength() && (e = (Element)nl.item(index)) != null) {
            if (FreeColGameObject.class.isAssignableFrom(returnClass)) {
                FreeColGameObject fcgo = (game == null)
                    ? ((Game.class.isAssignableFrom(returnClass)) ? new Game()
                        : null)
                    : game.getFreeColGameObject(readId(e));
                try {
                    ret = returnClass.cast(fcgo);
                } catch (ClassCastException cce) {}
            }
            if (ret == null && game != null) {
                ret = game.newInstance(returnClass, false);
            }
            if (ret != null) readFromXMLElement(ret, e);
        }
        return ret;
    }

    /**
     * Convenience method to extract all child elements of a
     * particular class.
     *
     * @param game The <code>Game</code> to instantiate within.
     * @param element The parent <code>Element</code>.
     * @param returnClass The expected class of the child.
     * @return A list of new instances of the return class.
     */
    public static <T extends FreeColObject> List<T> getChildren(Game game,
        Element element, Class<T> returnClass) {
        List<T> ret = new ArrayList<>();
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            T t = getChild(game, element, i, returnClass);
            if (t != null) ret.add(t);
        }
        return ret;
    }


    /**
     * Convenience method to map a function over the children of an Element.
     *
     * @param game The <code>Game</code> to instantiate within.
     * @param element The <code>Element</code> to extract children from.
     * @param mapper A mapper function.
     * @return A list of results of the mapping.
     */
    public static <T> List<T> mapChildren(Game game, Element element,
        Function<? super Element, ? extends T> mapper) {
        List<T> ret = new ArrayList<>();
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element)nl.item(i);
            if (e == null) continue;
            T x = mapper.apply((Element)nl.item(i));
            if (x != null) ret.add(x);
        }
        return ret;
    }

    /**
     * Get a boolean attribute value from an element.
     *
     * @param element The <code>Element</code> to query.
     * @param tag The attribute name.
     * @param defaultValue A default value to return on failure.
     * @return The boolean value found, or the default value on error.
     */
    public static boolean getBooleanAttribute(Element element, String tag,
                                              boolean defaultValue) {
        if (element != null && element.hasAttribute(tag)) {
            String str = element.getAttribute(tag);
            try {
                return Boolean.parseBoolean(str);
            } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    /**
     * Get an integer attribute value from an element.
     *
     * @param element The <code>Element</code> to query.
     * @param tag The attribute name.
     * @param defaultValue A default value to return on failure.
     * @return The integer value found, or the default value on error.
     */
    public static int getIntegerAttribute(Element element, String tag,
                                          int defaultValue) {
        if (element != null && element.hasAttribute(tag)) {
            String str = element.getAttribute(tag);
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    /**
     * Get a string attribute value from an element.
     *
     * @param element The <code>Element</code> to query.
     * @param tag The attribute name.
     * @param defaultValue A default value to return on failure.
     * @return The string value found, or the default value on error.
     */
    public static String getStringAttribute(Element element, String tag,
                                            String defaultValue) {
        if (element != null && element.hasAttribute(tag)) {
            return element.getAttribute(tag);
        }
        return defaultValue;
    }

    /**
     * Read a Document from an input source.
     * 
     * @param An <code>InputSource</code> to read from.
     * @return The resulting <code>Document</code>.
     * @exception IOException if thrown by the <code>InputStream</code>.
     * @exception SAXException if thrown during parsing.
     */
    public static Document readDocument(InputSource inputSource)
        throws SAXException, IOException {
        Document tempDocument = null;
        boolean dumpMsgOnError = true;
        if (dumpMsgOnError) {
            inputSource.setByteStream(new BufferedInputStream(inputSource.getByteStream()));

            inputSource.getByteStream().mark(1000000);
        }
        try {
            synchronized (parser) {
                tempDocument = parser.parse(inputSource);
            }
        } catch (IOException ex) {
            //} catch (IOException|SAXException ex) {
            throw ex;
        } catch (Exception ex) {
            // Xerces throws ArrayIndexOutOfBoundsException when it barfs on
            // some FreeCol messages. I'd like to see the messages upon which
            // it barfs.
            // Its also throwing SAXParseException in BR#2925
            if (dumpMsgOnError) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                inputSource.getByteStream().reset();
                while (true) {
                    int i = inputSource.getByteStream().read();
                    if (-1 == i) {
                        break;
                    }
                    baos.write(i);
                }
                logger.log(Level.SEVERE, baos.toString("UTF-8"), ex);
            } else {
                logger.log(Level.WARNING, "Parse error", ex);
            }
            throw ex;
        }
        return tempDocument;
    }

    /**
     * Convert an element to a string.
     *
     * @param element The <code>Element</code> to convert.
     * @return The <code>String</code> representation of an element.
     */
    public static String elementToString(Element element) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer xt = factory.newTransformer();
            StringWriter sw = new StringWriter();
            xt.transform(new DOMSource(element), new StreamResult(sw));
            String result = sw.toString();

            // Drop the <?xml...?> part if present to keep logging concise.
            if (result.startsWith("<?xml")) {
                final String xmlEnd = "?>";
                int index = result.indexOf(xmlEnd);
                if (index > 0) {
                    result = result.substring(index + xmlEnd.length());
                }
            }
            return result;
        } catch (TransformerException e) {
            logger.log(Level.WARNING, "TransformerException", e);
        }
        return null;
    }

    // @compat 0.10.x
    /**
     * Version of readId(FreeColXMLReader) that reads from an element.
     *
     * To be replaced with just:
     *   element.getAttribute(FreeColObject.ID_ATTRIBUTE_TAG);
     *
     * @param element An element to read the id attribute from.
     * @return The identifier attribute value.
     */
    public static String readId(Element element) {
        String id = element.getAttribute(FreeColObject.ID_ATTRIBUTE_TAG);
        if (id == null) id = element.getAttribute(FreeColObject.ID_ATTRIBUTE);
        return id;
    }
    // end @compat

    /**
     * Initialize a FreeColObject from an Element.
     *
     * @param fco The <code>FreeColObject</code> to read into.
     * @param element An XML-element that will be used to initialize
     *      the object.
     */
    public static void readFromXMLElement(FreeColObject fco, Element element) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer xmlTransformer = factory.newTransformer();
            StringWriter stringWriter = new StringWriter();
            xmlTransformer.transform(new DOMSource(element),
                                     new StreamResult(stringWriter));
            String xml = stringWriter.toString();
            try (
                FreeColXMLReader xr = new FreeColXMLReader(new StringReader(xml));
            ) {
                xr.nextTag();
                fco.readFromXML(xr);
            } catch (XMLStreamException xe) {
                throw new IllegalStateException("XML failure", xe);
            }
        } catch (IOException|TransformerException ex) {
            throw new RuntimeException("Read failure", ex);
        }
    }

    /**
     * This method writes an XML-representation of this object to
     * the given stream.
     *
     * Only attributes visible to the given <code>Player</code> will
     * be added to that representation if <code>showAll</code> is
     * set to <code>false</code>.
     *
     * @param fco The <code>FreeColObject</code> to write.
     * @param document The <code>Document</code>.
     * @param player The <code>Player</code> to send to, or to server if null.
     * @return An XML-representation of this object.
     */
    public static Element toXMLElement(FreeColObject fco, Document document,
                                       Player player) {
        return toXMLElement(fco, document, ((player == null)
                ? WriteScope.toServer()
                : WriteScope.toClient(player)), null);
    }

    /**
     * This method writes an XML-representation of this object to
     * the given stream.
     *
     * Only attributes visible to the given <code>Player</code> will
     * be added to that representation if <code>showAll</code> is
     * set to <code>false</code>.
     *
     * @param fco The <code>FreeColObject</code> to write.
     * @param document The <code>Document</code>.
     * @param writeScope The <code>WriteScope</code> to apply.
     * @return An XML-representation of this object.
     */
    public static Element toXMLElement(FreeColObject fco, Document document,
                                       WriteScope writeScope) {
        if (!writeScope.isValid()) {
            throw new IllegalStateException("Invalid write scope: "
                + writeScope);
        }
        return toXMLElement(fco, document, writeScope, null);
    }

    /**
     * This method writes a partial XML-representation of this object to
     * an element using only the mandatory and specified fields.
     *
     * @param fco The <code>FreeColObject</code> to write.
     * @param document The <code>Document</code>.
     * @param fields The fields to write.
     * @return An XML-representation of this object.
     */
    public static Element toXMLElementPartial(FreeColObject fco,
                                              Document document,
                                              String... fields) {
        return toXMLElement(fco, document, WriteScope.toServer(), fields);
    }

    /**
     * This method writes an XML-representation of this object to
     * the given stream.
     *
     * Only attributes visible to the given <code>Player</code> will
     * be added to that representation if <code>showAll</code> is
     * set to <code>false</code>.
     *
     * @param fco The <code>FreeColObject</code> to write.
     * @param document The <code>Document</code>.
     * @param writeScope The <code>WriteScope</code> to apply.
     * @param fields An array of field names, which if non-null
     *               indicates this should be a partial write.
     * @return An XML-representation of this object.
     */
    private static Element toXMLElement(FreeColObject fco, Document document,
                                        WriteScope writeScope, String[] fields) {
        StringWriter sw = new StringWriter();
        FreeColXMLWriter xw = null;
        try {
            xw = new FreeColXMLWriter(sw, writeScope);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error creating FreeColXMLWriter,", ioe);
            return null;
        }

        try {
            if (fields == null) {
                fco.toXML(xw);
            } else {
                fco.toXMLPartial(xw, fields);
            }
            xw.close();

            DocumentBuilderFactory factory
                = DocumentBuilderFactory.newInstance();
            Document tempDocument = null;
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                tempDocument = builder.parse(new InputSource(new StringReader(sw.toString())));
                return (Element)document.importNode(tempDocument.getDocumentElement(), true);
            } catch (IOException|ParserConfigurationException|SAXException ex) {
                throw new RuntimeException("Parse fail", ex);
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Error writing stream", e);
        }
    }


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getElement().toString();
    }
}
