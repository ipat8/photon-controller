DEPS=common host pthrift scheduler
TESTS=agent/tests/unit
EXTRA_CLEANUP_TARGETS=$(BUILD)/$(NAME).log

ifdef INTEGRATION
ifdef REMOTE_SERVER
	TEST_OPTS += --tc=agent_remote_test.server:$(REMOTE_SERVER) -s \
                     --tc agent_remote_stress_test.host:$(REMOTE_SERVER)
endif
ifdef DATASTORES
	TEST_OPTS += --tc=agent_remote_test.datastores:$(DATASTORES)
endif
ifdef REMOTE_ISO
	TEST_OPTS += --tc=agent_remote_test.iso_file:"$(REMOTE_ISO)"
endif
ifdef AGENT_IN_UWSIM
	TEST_OPTS += --tc=agent_remote_test.agent_in_uwsim:1
endif
ifdef ZOOKEEPER
	TEST_OPTS += --tc=agent_remote_test.zookeeper:$(ZOOKEEPER)
endif
ifdef CHAIRMAN
	TEST_OPTS += --tc=agent_remote_test.chairman:$(CHAIRMAN)
endif
	TESTS += agent/tests/integration
endif

include ../python.mk
