/**
 * Copyright 2011 Intellectual Reserve, Inc.
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
 */
package org.gedcomx.build.enunciate;

import freemarker.ext.beans.BeansWrapper;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.codehaus.enunciate.contract.jaxb.*;
import org.codehaus.enunciate.contract.jaxb.types.XmlClassType;
import org.codehaus.enunciate.contract.jaxb.types.XmlType;
import org.codehaus.enunciate.doc.DocumentationExample;
import org.codehaus.enunciate.util.WhateverNode;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

import javax.xml.namespace.QName;
import java.io.StringWriter;
import java.util.List;
import java.util.Stack;

/**
 * @author Ryan Heaton
 */
public class GenerateExampleJsonMethod implements TemplateMethodModelEx {

  /**
   * Stack used for maintaining the list of type definitions for which we are currently generating example xml/json. Used to
   * prevent infinite recursion for circular references.
   */
  private static final ThreadLocal<Stack<String>> TYPE_DEF_STACK = new ThreadLocal<Stack<String>>();

  public Object exec(List list) throws TemplateModelException {
    if (list.size() < 1) {
      throw new TemplateModelException("The generateExampleJson method must have a root element as a parameter.");
    }

    Object object = BeansWrapper.getDefaultInstance().unwrap((TemplateModel) list.get(0));
    TypeDefinition type;
    if (object instanceof RootElementDeclaration) {
      RootElementDeclaration rootEl = (RootElementDeclaration) object;
      type = rootEl.getTypeDefinition();
    }
    else if (object instanceof TypeDefinition) {
      type = (TypeDefinition) object;
    }
    else {
      throw new TemplateModelException("The generateExampleJson method must have a root element as a parameter.");
    }

    try {
      ObjectNode node = generateExampleJson(type);
      StringWriter sw = new StringWriter();
      JsonGenerator generator = new JsonFactory().createJsonGenerator(sw);
      generator.useDefaultPrettyPrinter();
      node.serialize(generator, null);
      generator.flush();
      sw.flush();
      return sw.toString();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ObjectNode generateExampleJson(TypeDefinition type) {
    if (TYPE_DEF_STACK.get() == null) {
      TYPE_DEF_STACK.set(new Stack<String>());
    }

    ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
    generateExampleJson(type, jsonNode);
    return jsonNode;
  }

  protected void generateExampleJson(TypeDefinition type, ObjectNode jsonNode) {
    if (TYPE_DEF_STACK.get().contains(type.getQualifiedName())) {
      jsonNode.put("...", WhateverNode.instance);
    }
    else {
      TYPE_DEF_STACK.get().push(type.getQualifiedName());
      if (!jsonNode.has("@type") && type.getAnnotation(JsonTypeInfo.class) != null) {
        jsonNode.put("@type", JsonNodeFactory.instance.textNode(type.getQname().toString()));
      }
      for (Attribute attribute : type.getAttributes()) {
        generateExampleJson(attribute, jsonNode);
      }
      if (type.getValue() != null) {
        generateExampleJson(type.getValue(), jsonNode);
      }
      else {
        for (Element element : type.getElements()) {
          generateExampleJson(element, jsonNode);
        }
      }
      TYPE_DEF_STACK.get().pop();
    }


    XmlType baseType = type.getBaseType();
    if (baseType instanceof XmlClassType) {
      TypeDefinition typeDef = ((XmlClassType) baseType).getTypeDefinition();
      if (typeDef != null) {
        generateExampleJson(typeDef, jsonNode);
      }
    }
  }

  protected void generateExampleJson(Attribute attribute, ObjectNode jsonNode) {
    DocumentationExample exampleInfo = attribute.getAnnotation(DocumentationExample.class);
    if (exampleInfo == null || !exampleInfo.exclude()) {
      JsonNode valueNode = generateExampleJson(attribute.getBaseType(), exampleInfo == null || "##default".equals(exampleInfo.value()) ? null : exampleInfo.value());
      jsonNode.put(attribute.getJsonMemberName(), valueNode);
    }
  }

  protected void generateExampleJson(Value value, ObjectNode jsonNode) {
    DocumentationExample exampleInfo = value.getAnnotation(DocumentationExample.class);
    if (exampleInfo == null || !exampleInfo.exclude()) {
      JsonNode valueNode = generateExampleJson(value.getBaseType(), exampleInfo == null || "##default".equals(exampleInfo.value()) ? null : exampleInfo.value());
      jsonNode.put(value.getJsonMemberName(), valueNode);
    }
  }

  protected void generateExampleJson(Element element, ObjectNode jsonNode) {
    DocumentationExample exampleInfo = element.getAnnotation(DocumentationExample.class);
    if (exampleInfo == null || !exampleInfo.exclude()) {
      String name = element.getJsonMemberName();
      JsonNode elementNode;
      if (!element.isCollectionType()) {
        String exampleValue = exampleInfo == null || "##default".equals(exampleInfo.value()) ? "..." : exampleInfo.value();
        if (element.getRef() == null) {
          elementNode = generateExampleJson(element.getBaseType(), exampleValue);
        }
        else {
          elementNode = JsonNodeFactory.instance.objectNode();
        }
      }
      else {
        ArrayNode exampleChoices = JsonNodeFactory.instance.arrayNode();
        for (Element choice : element.getChoices()) {
          QName ref = choice.getRef();
          int iterations = "1".equals(choice.getMaxOccurs()) ? 1 : 2;
          for (int i = 0; i < iterations; i++) {
            if (ref == null) {
              String exampleValue = exampleInfo == null || "##default".equals(exampleInfo.value()) ? null : exampleInfo.value();
              XmlType xmlType = choice.getBaseType();
              if (i == 0) {
                exampleChoices.add(generateExampleJson(xmlType, exampleValue));
              }
              else {
                exampleChoices.add(WhateverNode.instance);
              }
            }
            else {
              exampleChoices.add(JsonNodeFactory.instance.objectNode());
            }
          }
        }
        elementNode = exampleChoices;
      }
      jsonNode.put(name, elementNode);
    }
  }

  protected JsonNode generateExampleJson(XmlType type, String value) {
    if (type instanceof XmlClassType) {
      return generateExampleJson(((XmlClassType) type).getTypeDefinition());
    }
    else {
      return type.generateExampleJson(value);
    }
  }

}