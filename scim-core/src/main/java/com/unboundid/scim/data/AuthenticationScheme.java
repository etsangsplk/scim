/*
 * Copyright 2011 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim.data;

import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.sdk.InvalidResourceException;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeValue;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the AuthenticationSchemes complex attribute in the
 * Service Provider Config.
 */
public class AuthenticationScheme
{
  /**
   * The <code>AttributeValueResolver</code> that resolves SCIM attribute values
   * to/from <code>AuthenticationScheme</code> instances.
   */
  public static final AttributeValueResolver<AuthenticationScheme>
      AUTHENTICATION_SCHEME_RESOLVER =
      new AttributeValueResolver<AuthenticationScheme>()
      {
        public AuthenticationScheme toInstance(final SCIMAttributeValue value) {
          Boolean p = value.getSingularSubAttributeValue("primary",
                  BOOLEAN_RESOLVER);
          return new AuthenticationScheme(
              value.getSingularSubAttributeValue("name",
                  STRING_RESOLVER),
              value.getSingularSubAttributeValue("description",
                  STRING_RESOLVER),
              value.getSingularSubAttributeValue("specUrl",
                  STRING_RESOLVER),
              value.getSingularSubAttributeValue("documentationUrl",
                  STRING_RESOLVER),
              value.getSingularSubAttributeValue("type",
                  STRING_RESOLVER),
              p == null ? false : p);
        }

        @Override
        public SCIMAttributeValue fromInstance(
            final AttributeDescriptor addressDescriptor,
            final AuthenticationScheme value)
            throws InvalidResourceException {
          final List<SCIMAttribute> subAttributes =
              new ArrayList<SCIMAttribute>(8);

          if (value.type != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("type"),
                    SCIMAttributeValue.createStringValue(value.type)));
          }

          if (value.name != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("name"),
                    SCIMAttributeValue.createStringValue(value.name)));
          }

          if (value.description != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("description"),
                    SCIMAttributeValue.createStringValue(value.description)));
          }

          if (value.specUrl != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("specUrl"),
                    SCIMAttributeValue.createStringValue(value.specUrl)));
          }

          if (value.documentationUrl != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("documentationUrl"),
                    SCIMAttributeValue.createStringValue(
                        value.documentationUrl)));
          }

          if (value.primary)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    addressDescriptor.getSubAttribute("primary"),
                    SCIMAttributeValue.createBooleanValue(value.primary)));
          }

          return SCIMAttributeValue.createComplexValue(subAttributes);
        }
      };



  private String name;
  private String description;
  private String specUrl;
  private String documentationUrl;
  private String type;
  private boolean primary;

  /**
   * Create a value of the SCIM AuthenticationSchemes attribute.
   *
   * @param name              The name of the Authentication Scheme.
   * @param description       The description of the Authentication Scheme.
   * @param specUrl           A HTTP addressable URL pointing to the
   *                          Authentication Scheme's specification.
   * @param documentationUrl  A HTTP addressable URL pointing to the
   *                          Authentication Scheme's usage documentation.
   * @param type              The type of Authentication Scheme.
   * @param primary           Specifies whether this value is the primary value.
   */
  public AuthenticationScheme(final String name,
                              final String description,
                              final String specUrl,
                              final String documentationUrl,
                              final String type,
                              final boolean primary) {
    this.name = name;
    this.description = description;
    this.specUrl = specUrl;
    this.documentationUrl = documentationUrl;
    this.primary = primary;
    this.type = type;
  }

  /**
   * Retrieves the name of the Authentication Scheme.
   *
   * @return The name of the Authentication Scheme.
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the Authentication Scheme.
   *
   * @param name The name of the Authentication Scheme.
   */
  public void setName(final String name) {
    this.name = name;
  }

  /**
   * Retrieves the description of the Authentication Scheme.
   *
   * @return The description of the Authentication Scheme.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description of the Authentication Scheme.
   *
   * @param description The description of the Authentication Scheme.
   */
  public void setDescription(final String description) {
    this.description = description;
  }

  /**
   * Retrieves the HTTP addressable URL pointing to the Authentication Scheme's
   * specification.
   *
   * @return The the HTTP addressable URL pointing to the Authentication
   *         Scheme's specification, or {@code null} if there is none.
   */
  public String getSpecUrl() {
    return specUrl;
  }

  /**
   * Sets the HTTP addressable URL pointing to the Authentication Scheme's
   * specification.
   * @param specUrl The HTTP addressable URL pointing to the Authentication
   *                Scheme's specification.
   */
  public void setSpecUrl(final String specUrl) {
    this.specUrl = specUrl;
  }

  /**
   * Retrieves the HTTP addressable URL pointing to the Authentication Scheme's
   * usage documentation.
   * @return The HTTP addressable URL pointing to the Authentication Scheme's
   *         usage documentation.
   */
  public String getDocumentationUrl() {
    return documentationUrl;
  }

  /**
   * Sets the HTTP addressable URL pointing to the Authentication Scheme's
   * usage documentation.
   * @param documentationUrl The HTTP addressable URL pointing to the
   *                         Authentication Scheme's usage documentation.
   */
  public void setDocumentationUrl(final String documentationUrl) {
    this.documentationUrl = documentationUrl;
  }

  /**
   * Indicates whether this value is the primary value.
   *
   * @return <code>true</code> if this value is the primary value or
   * <code>false</code> otherwise.
   */
  public boolean isPrimary() {
    return primary;
  }

  /**
   * Specifies whether this value is the primary value.
   *
   * @param primary Whether this value is the primary value.
   */
  public void setPrimary(final boolean primary) {
    this.primary = primary;
  }

  /**
   * Retrieves the type of Authentication Scheme.
   *
   * @return The type of Authentication Scheme.
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the type of Authentication Scheme.
   *
   * @param type The type of Authentication Scheme.
   */
  public void setType(final String type) {
    this.type = type;
  }

}