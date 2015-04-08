/* 
 * Spoon - http://spoon.gforge.inria.fr/
 * Copyright (C) 2006 INRIA Futurs 
 * <renaud.pawlak@inria.fr,Didier.Donsez@ieee.org>
 * 
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify 
 * and/or redistribute the software under the terms of the CeCILL-C license as 
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *  
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */

package spoon.processing;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.processing.XmlProcessorProperties;

/**
 * A processor to add/replace/override/remove annotations described in an XML
 * file. This version is based on java.util.regexp, but it can be subclassed to
 * implement other types of matchings. Note that the used XML file is defined by
 * the property {@link #xmlPath}, which can be defined in a property file or a
 * Spoonlet file (spoon.xml) depending on your environment.
 * 
 * @author Didier Donsez
 * @author Renaud Pawlak
 */
public class XMLAnnotationProcessor extends AbstractManualProcessor {

	/**
	 * This property contains the path of the XML file that describes the
	 * annotations of the processed program. By default, the file is the
	 * annotations.xml file contained in the current directory.
	 */
	@Property
	public String xmlPath = "annotations.xml";

	private Document document;

	/**
	 * Creates a new XMLAnnotationProcessor.
	 */
	public XMLAnnotationProcessor() {
	}

	@Override
	final public void init() {
		// Initiate DocumentBuilderFactory
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		// To get a validating parser
		factory.setValidating(false); // since no DTD/XML Schema is defined
		// To get one that understands namespaces
		factory.setNamespaceAware(true);

		try {
			// Get DocumentBuilder
			DocumentBuilder builder = factory.newDocumentBuilder();
			// Parse and load into memory the Document
			document = builder.parse(getClass().getClassLoader()
					.getResourceAsStream(xmlPath));
		} catch (SAXParseException spe) {
			// Error generated by the parser
			getEnvironment().report(
					this,
					Severity.ERROR,
					"XML parsing error line " + spe.getLineNumber() + ", uri "
							+ spe.getSystemId());
			// Use the contained exception, if any
			Exception x = spe;
			if (spe.getException() != null)
				x = spe.getException();
			Launcher.logger.error(x.getMessage(), x);
		} catch (SAXException sxe) {
			// Error generated during parsing
			Exception x = sxe;
			if (sxe.getException() != null)
				x = sxe.getException();
			Launcher.logger.error(x.getMessage(), x);
		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			Launcher.logger.error(pce.getMessage(), pce);
		} catch (IOException ioe) {
			// I/O error
			Launcher.logger.error(ioe.getMessage(), ioe);
		}
	}

	/**
	 * Override this method to create other types of matchings than the default
	 * one (java.util.regexp).
	 * 
	 * @param type
	 *            the candidate type
	 * @param typeExpression
	 *            and expression to match the type against.
	 * @return true if the type matches the expression
	 * @see CtElement#getSignature()
	 */
	protected boolean isTypeMatching(CtType<?> type, String typeExpression) {
		return java.util.regex.Pattern.matches(typeExpression,
				type.getQualifiedName());
	}

	/**
	 * Override this method to create other types of matchings than the default
	 * one (java.util.regexp).
	 * 
	 * @param executable
	 *            the candidate executable
	 * @param executableExpression
	 *            and expression to match the executable against.
	 * @return true if the executable matches the expression
	 * @see CtElement#getSignature()
	 */
	protected boolean isExecutableMatching(CtExecutable<?> executable,
			String executableExpression) {
		String signature = executable.getSignature();
		return java.util.regex.Pattern.matches(executableExpression, signature);
	}

	/**
	 * Override this method to create other types of matchings than the default
	 * one (java.util.regexp).
	 * 
	 * @param field
	 *            the candidate field
	 * @param fieldExpression
	 *            and expression to match the field against.
	 * @return true if the field matches the expression
	 * @see CtElement#getSignature()
	 */
	protected boolean isFieldMatching(CtField<?> field, String fieldExpression) {
		String signature = field.getSignature();
		return java.util.regex.Pattern.matches(fieldExpression, signature);
	}

	final public void process() {
		// Get the XML root node
		Element root = document.getDocumentElement();

		NodeList nodeList = root.getElementsByTagName("class");
		int len = nodeList.getLength();
		for (int i = 0; i < len; i++) {
			Element clazz = (Element) nodeList.item(i);
			String nameExpr = clazz.getAttribute("expr");
			for (CtType<?> t : getFactory().Type().getAll(true)) {
				// CtType<?> t = getType(name);
				if (!isTypeMatching(t, nameExpr))
					continue;
				try {
					annotateElement(t, clazz);
				} catch (Exception e) {
					Launcher.logger.error(e.getMessage(), e);
				}

				NodeList nodeList3 = clazz.getElementsByTagName("field");
				for (int j = 0; j < nodeList3.getLength(); j++) {
					Element fieldElt = (Element) nodeList3.item(j);
					if (fieldElt.getParentNode() != clazz)
						continue;
					String fieldExpr = fieldElt.getAttribute("expr");

					for (CtField<?> field : t.getFields()) {
						if (!isFieldMatching(field, fieldExpr))
							continue;
						try {
							annotateElement(field, fieldElt);
						} catch (Exception e) {
							Launcher.logger.error(e.getMessage(), e);
						}
					}
				}

				if (!(t instanceof CtType))
					continue;

				List<CtExecutable<?>> executables = new ArrayList<CtExecutable<?>>();
				executables.addAll(((CtType<?>) t).getMethods());
				if (t instanceof CtClass) {
					executables.addAll(((CtClass<?>) t).getConstructors());
				}

				NodeList nodeList2 = clazz.getElementsByTagName("executable");
				for (int j = 0; j < nodeList2.getLength(); j++) {
					Element executableElt = (Element) nodeList2.item(j);
					if (executableElt.getParentNode() != clazz)
						continue;
					String executableExpr = executableElt.getAttribute("expr");

					for (CtExecutable<?> executable : executables) {

						if (!isExecutableMatching(executable, executableExpr))
							continue;
						try {
							annotateElement(executable, executableElt);
						} catch (Exception e) {
							Launcher.logger.error(e.getMessage(), e);
						}

						// NodeList paramList = method
						// .getElementsByTagName("parameter");
						// for (int k = 0; k < paramList.getLength(); k++) {
						// Element param = (Element) paramList.item(k);
						// if (param.getParentNode() != method)
						// continue;
						// String paramName = param.getAttribute("name");
						// CtParameter<?> parameter = getParameter(exec,
						// paramName);
						// if (parameter != null) {
						// try {
						// annotateElement(parameter, param);
						// } catch (Exception e) {
						// Launcher.logger.error(e.getMessage(), e);
						// }
						// }
						// }

					}
				}

			}
		}
	}

	// private CtType<?> getType(String name) {
	// CtTypeReference<?> ref = getFactory().Type().createReference(name);
	// CtType<?> t = ref.getDeclaration();
	// return t;
	// }
	//
	// private CtExecutable<?> getExecutable(String signature) {
	// CtExecutableReference<?> ref = getFactory().Executable()
	// .createReference(signature);
	// CtExecutable<?> t = ref.getDeclaration();
	// return t;
	// }
	//
	// private CtField<?> getField(String signature) {
	// CtFieldReference<?> ref = getFactory().Field().createReference(
	// signature);
	// CtField<?> t = ref.getDeclaration();
	// return t;
	// }
	//
	// private CtParameter<?> getParameter(CtExecutable<?> exec, String name) {
	// for (CtParameter<?> p : exec.getParameters()) {
	// if (p.getSimpleName().equals(name)) {
	// return p;
	// }
	// }
	// return null;
	// }

	private void annotateElement(CtElement javaElt, Element xmlNode)
			throws Exception {
		NodeList annNodeList = xmlNode.getElementsByTagName("annotation");
		for (int i = 0; i < annNodeList.getLength(); i++) {
			Element annotationNode = (Element) annNodeList.item(i);
			if (annotationNode.getParentNode() != xmlNode)
				continue;
			String name = annotationNode.getAttribute("name");
			CtTypeReference<? extends Annotation> aref = getFactory()
					.Annotation().createReference(name);
			getFactory().Annotation().annotate(javaElt, aref);
			NodeList fieldNodeList = annotationNode
					.getElementsByTagName("element");
			for (int f = 0; f < fieldNodeList.getLength(); f++) {
				Element fieldNode = (Element) fieldNodeList.item(f);
				String fieldName = fieldNode.getAttribute("name");
				String fieldValue = fieldNode.getAttribute("value");
				Class<?> type = aref.getActualClass().getMethod(fieldName)
						.getReturnType();
				Object v = ((XmlProcessorProperties) getEnvironment().getProcessorProperties(this.getClass().getName())).convert(type, fieldValue);
				getFactory().Annotation().annotate(javaElt, aref, fieldName, v);
			}
		}
	}

}
