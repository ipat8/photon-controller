# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

module EsxCloud
  class DeploymentCreateSpec

    attr_accessor :image_datastore, :auth, :syslog_endpoint, :ntp_endpoint, :use_image_datastore_for_vms,
                  :loadbalancer_enabled

    # @param [String] image_datastore
    # @param [AuthInfo] auth
    # @param [String] syslog_endpoint
    # @param [String] ntp_endpoint
    # @param [Boolean] use_image_datastore_for_vms
    def initialize(image_datastore, auth,
      syslog_endpoint = nil, ntp_endpoint = nil, use_image_datastore_for_vms = false,
      loadbalancer_enabled = true)
      fail EsxCloud::UnexpectedFormat, "auth is class #{auth.class} instead of AuthInfo" unless auth.is_a?(AuthInfo)
      @image_datastore = image_datastore
      @auth = auth
      @syslog_endpoint = syslog_endpoint
      @ntp_endpoint = ntp_endpoint
      @use_image_datastore_for_vms = use_image_datastore_for_vms
      @loadbalancer_enabled = loadbalancer_enabled
    end

    def to_hash
      {
        imageDatastore: @image_datastore,
        auth: @auth.to_hash,
        syslogEndpoint: @syslog_endpoint,
        ntpEndpoint: @ntp_endpoint,
        useImageDatastoreForVms: @use_image_datastore_for_vms
      }
    end

    def ==(other)
      @image_datastore == other.image_datastore &&
        @auth == other.auth &&
        @syslog_endpoint == other.syslog_endpoint &&
        @ntp_endpoint == other.ntp_endpoint &&
        @use_image_datastore_for_vms == other.use_image_datastore_for_vms
    end
  end
end
