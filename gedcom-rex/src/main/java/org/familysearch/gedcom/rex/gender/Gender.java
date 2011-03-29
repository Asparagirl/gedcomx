package org.familysearch.gedcom.rex.gender;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import org.familysearch.gedcom.rex.Field;

/**
 * @author Merlin Carpenter
 *         Date: Jul 30, 2008
 */
@XmlType
public class Gender extends Field {

  private GenderType type;

  public Gender() {
  }

  @XmlAttribute
  public GenderType getType() {
    return type;
  }

  public void setType(GenderType type) {
    this.type = type;
  }

}
