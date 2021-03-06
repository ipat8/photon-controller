# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

import common

from common.service_name import ServiceName
from gen.host import Host
from host.host_handler import HostHandler
from host.hypervisor import hypervisor
from host.hypervisor.esx.hypervisor import EsxHypervisor

# Load agent config and registrant
try:
    config = common.services.get(ServiceName.AGENT_CONFIG)
    registrant = common.services.get(ServiceName.REGISTRANT)
except Exception as e:
    raise ImportError(e)

# Create the hypervisor object
hv = hypervisor.Hypervisor(config)

# When datastore/network changes on the host, let chairman know
hv.add_update_listener(registrant)

# When configuration changes, notify hypervisor
config.on_config_change(config.CPU_OVERCOMMIT, hv.set_cpu_overcommit)
config.on_config_change(config.MEMORY_OVERCOMMIT, hv.set_memory_overcommit)

# Register hypervisor in services
common.services.register(ServiceName.HYPERVISOR, hv)

# Create host handler
host_handler = HostHandler(hv)
common.services.register(Host.Iface, host_handler)
if type(hv.hypervisor) == EsxHypervisor:
    common.services.register(ServiceName.VIM_CLIENT, hv.hypervisor.vim_client)

# Load num_threads
try:
    num_threads = config.host_service_threads
except Exception as e:
    raise ImportError(e)

# Define host plugin
plugin = common.plugin.Plugin(
    name="Host",
    service=Host,
    handler=host_handler,
    num_threads=num_threads,
)
