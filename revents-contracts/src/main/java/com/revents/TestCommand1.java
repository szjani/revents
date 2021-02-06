package com.revents;

import org.immutables.value.Value;

@Value.Immutable
interface TestCommand1 extends BaseTestCommand {

    String useThisId();
}
