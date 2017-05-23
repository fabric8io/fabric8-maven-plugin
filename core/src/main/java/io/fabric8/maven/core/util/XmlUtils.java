/**
 *  Copyright 2005-2016 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.maven.core.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XML related utilities.
 */
public class XmlUtils {

    private XmlUtils() {
        // utility class
    }

    private static TransformerFactory transformerFactory;
    private static Transformer transformer;

    public static Document parseDoc(File xmlFile)
            throws ParserConfigurationException,
            SAXException,
            IOException {
        try (InputStream is = new FileInputStream(xmlFile)) {
            BufferedInputStream in = new BufferedInputStream(is);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(in);
            return builder.parse(source);
        }
    }

        /**
     * Returns the first child element for the given name
     */
    public static Element firstChild(Element element, String name) {
        NodeList nodes = element.getChildNodes();
        if (nodes != null) {
            for (int i = 0, size = nodes.getLength(); i < size; i++) {
                Node item = nodes.item(i);
                if (item instanceof Element) {
                    Element childElement = (Element) item;

                    if (name.equals(childElement.getTagName())) {
                        return childElement;
                    }
                }
            }
        }
        return null;
    }

    public static String firstChildTextContent(Element element, String name) {
        Element child = firstChild(element, name);
        if (child != null) {
            return child.getTextContent();
        }
        return null;
    }

    public static void removeChildren(Element element) {
        while (true) {
            Node child = element.getFirstChild();
            if (child == null) {
                return;
            }
            element.removeChild(child);
        }
    }

        public static Element addChildElement(Node parent, String elementName) {
        Document ownerDocument = parent.getOwnerDocument();
        Objects.requireNonNull(ownerDocument, "nodes ownerDocument " + parent);
        Element element = ownerDocument.createElement(elementName);
        parent.appendChild(element);
        return element;
    }

    public static Element addChildElement(Node parent, String elementName, String textContent) {
        Element element = addChildElement(parent, elementName);
        element.setTextContent(textContent);
        return element;
    }

    public static void save(Document document, File file) throws FileNotFoundException, TransformerException {
        Transformer transformer = getTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(new FileOutputStream(file)));
    }


    // ================================================================================================

    private static String getTextContent(final Node node) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
            case Node.ENTITY_NODE:
            case Node.ENTITY_REFERENCE_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                return mergeTextContent(node.getChildNodes());
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
            case Node.COMMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                return node.getNodeValue();
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_TYPE_NODE:
            case Node.NOTATION_NODE:
            default:
                return null;
        }
    }

    private static String mergeTextContent(final NodeList nodes) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            final String text;

            switch (n.getNodeType()) {
                case Node.COMMENT_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE:
                    // ignore comments when merging
                    text = null;
                    break;
                default:
                    text = getTextContent(n);
                    break;
            }

            if (text != null) {
                buf.append(text);
            }
        }
        return buf.toString();
    }

    private static Transformer getTransformer() throws TransformerConfigurationException {
        if (transformer == null) {
            transformer = getTransformerFactory().newTransformer();
        }
        return transformer;
    }

    private static TransformerFactory getTransformerFactory() {
        if (transformerFactory == null){
            transformerFactory = TransformerFactory.newInstance();
        }
        return transformerFactory;
    }

}
