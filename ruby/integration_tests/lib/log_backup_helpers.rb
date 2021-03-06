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

require "yaml"
require_relative "../spec/support/copy_log_files_helper"
require_relative "test_helpers"
require_relative "../lib/integration"
require_relative "../spec/support/api_client_helper"

include EsxCloud::TestHelpers

module EsxCloud
   class LogBackUpHelpers

     class << self

       def download_esx_logs
         esx_hosts = [get_esx_ip] # On devbox this is THE host

         # For inf2, hosts are available for REST call query
         begin
           EsxCloud::Config.init
           EsxCloud::Config.client = ApiClientHelper.management
           esx_hosts += EsxCloud::Host.find_all.items.map { |h| h.address }
         rescue Exception => e
           puts "download_esx_logs: fail to list hosts\n" + e.to_s
         end

         puts "downloading logs from #{esx_hosts}"
         esx_hosts.uniq.select { |h| !h.nil? && !h.empty? }.sort.each do |host|
           user_name = "root"
           password = "ca$hc0w"
           esx_log_folder = "/var/log"
           dest_folder = "./reports/log"

           if !server_up?(host)
             puts "#{host} is down and the esx logs cannot be retrieved."
             next
           end

           remove_host_from_known_hosts_file(host)

           get_esx_log_files(host, user_name, password, esx_log_folder).each do |file_name|
             target_file_name = regulate_file_name(host, file_name)
             puts "Downloading #{esx_log_folder}/#{file_name} from #{host} to #{dest_folder}/#{target_file_name} ..."
             begin
               puts "File #{esx_log_folder}/#{file_name} does not exist on #{host}" unless
                 download_file(host, user_name, password, esx_log_folder, file_name, dest_folder, target_file_name)
             rescue
               puts "Failed to retrieve #{esx_log_folder}/#{file_name} from #{host}"
             end
           end
         end
       end

       private

       # @param [String] host
       # @param [String] file
       def regulate_file_name(host, file)
         ext = File.extname file
         name = File.basename file, ext

         name + "-" + host + ext
       end

       def get_esx_log_files(server, user_name, password, esx_log_folder)
         Net::SSH.start(server, user_name, password: password) do |ssh|
           output = ssh.exec!("ls #{esx_log_folder}").strip
           log_files = output.gsub(/\s+/m, " ").split(" ").select do |f|
             ["photon-controller-agent", "hostd", "vmk"].find { |prefix| f.start_with? prefix }
           end

           puts "Log files on #{server}: #{log_files}"
           log_files
         end
       end

     end
  end
end
