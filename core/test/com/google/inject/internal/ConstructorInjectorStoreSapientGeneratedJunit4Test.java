package com.google.inject.internal;

import org.junit.rules.Timeout;
import org.junit.Rule;
import org.junit.Test;
import com.google.inject.spi.InjectionPoint;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.is;

import org.junit.Ignore;

public class ConstructorInjectorStoreSapientGeneratedJunit4Test {

    @Rule()
    public Timeout timeoutRule = Timeout.seconds(5);

    private final InjectionPoint injectionPointMock = mock(InjectionPoint.class);

    private final InjectorImpl injectorImplMock = mock(InjectorImpl.class);

    //Sapient generated method id: ${07406e90-65c4-3a1d-917b-6a6b045d4d2e}, hash: 55AF547D3E4DFC7A1595AABB6C965F2C
    @Test()
    public void isLoadingWhenCacheNotIsLoadingIp() {
        /* Branches:
         * (cache.isLoading(ip)) : false
         *
         * TODO: Help needed! This method is not unit testable!
         *  Following variables could not be isolated/mocked: cache
         *  Suggestions:
         *  You can pass them as constructor arguments or create a setter for them (avoid new operator)
         *  or adjust the input/test parameter values manually to satisfy the requirements of the given test scenario.
         *  The test code, including the assertion statements, has been successfully generated.
         */
        //Arrange Statement(s)
        ConstructorInjectorStore target = new ConstructorInjectorStore(injectorImplMock);
        //Act Statement(s)
        boolean result = target.isLoading(injectionPointMock);
        //Assert statement(s)
        assertThat(result, equalTo(Boolean.FALSE));
    }

    //Sapient generated method id: ${61a69fac-848e-37bc-bb7e-cf249a8b5033}, hash: DDBE99A723AD7010FDB3828C888E2AA5
    @Test()
    public void removeWhenCacheNotRemoveIp() {
        /* Branches:
         * (cache.remove(ip)) : false
         *
         * TODO: Help needed! This method is not unit testable!
         *  Following variables could not be isolated/mocked: cache
         *  Suggestions:
         *  You can pass them as constructor arguments or create a setter for them (avoid new operator)
         *  or adjust the input/test parameter values manually to satisfy the requirements of the given test scenario.
         *  The test code, including the assertion statements, has been successfully generated.
         */
        //Arrange Statement(s)
        ConstructorInjectorStore target = new ConstructorInjectorStore(injectorImplMock);
        //Act Statement(s)
        boolean result = target.remove(injectionPointMock);
        //Assert statement(s)
        assertThat(result, equalTo(Boolean.FALSE));
    }
}
