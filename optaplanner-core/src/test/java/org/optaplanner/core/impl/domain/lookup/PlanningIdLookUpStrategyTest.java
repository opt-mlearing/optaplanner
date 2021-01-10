package org.optaplanner.core.impl.domain.lookup;

import org.junit.jupiter.api.Test;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.domain.common.accessor.MemberAccessor;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author caogaoli@baidu.com
 * @date 2021.01.10
 */
public class PlanningIdLookUpStrategyTest {

    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    public void test_if_in_TestDataSolution() {
        Class<?> clz = TestdataSolution.class;
        MemberAccessor memberAccessor = ConfigUtils.findPlanningIdMemberAccessor(clz);
        logger.info("member accessor {}", memberAccessor);
    }

}
