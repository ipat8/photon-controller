/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;

import org.hibernate.validator.constraints.NotBlank;
import static com.google.common.base.Objects.ToStringHelper;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import java.util.Objects;

/**
 * ImageSettings entity.
 */
@Entity(name = "ImageSettings")
@Table(name = "image_settings")
@NamedQueries({
    @NamedQuery(
        name = "ImageSettings.listAll",
        query = "SELECT s FROM ImageSettings s"
    )
})
public class ImageSettingsEntity extends BaseEntity {

  public static final String KIND = "imageSettingsEntity";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "image", nullable = false)
  private ImageEntity image;

  @NotBlank
  @Column(name = "name")
  private String name;

  @Column(name = "default_value")
  private String defaultValue;

  @Override
  public String getKind() {
    return KIND;
  }

  public ImageEntity getImage() {
    return image;
  }

  public void setImage(ImageEntity image) {
    this.image = image;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  @Override
  protected ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("image", image.getId())
        .add("name", name)
        .add("defaultValue", defaultValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ImageSettingsEntity)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    ImageSettingsEntity that = (ImageSettingsEntity) o;

    return Objects.equals(image, that.image)
        && Objects.equals(name, that.name)
        && Objects.equals(defaultValue, that.defaultValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        image,
        name,
        defaultValue);
  }
}
