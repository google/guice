package com.google.inject.internal;

import org.junit.rules.Timeout;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.hamcrest.Matchers.is;

public class ConstructionContextTest {

    @Rule()
    public Timeout timeoutRule = Timeout.seconds(5);

    private final InjectorImpl.InjectorOptions injectorImplInjectorOptionsMock = mock(InjectorImpl.InjectorOptions.class);

    @Rule()
    public ExpectedException thrown = ExpectedException.none();

    //Sapient generated method id: ${fd29cd0f-f5b1-30b4-8cda-768672f68aba}, hash: E66F5CF26FE199A2F2B2C170E4932242
    @Test()
    public void removeCurrentReferenceTest() {
        //Arrange Statement(s)
        ConstructionContext<Object> target = new ConstructionContext<>();
        //Act Statement(s)
        target.removeCurrentReference();
        //Assert statement(s)
        assertThat(target.getCurrentReference(), is(nullValue()));
    }

    //Sapient generated method id: ${5af195be-742d-303b-b6b2-671a9ad7deb4}, hash: F27F08A7B477319B751779BD074389FA
    @Test()
    public void startConstructionTest() {
        //Arrange Statement(s)
        ConstructionContext<Object> target = new ConstructionContext<>();
        //Act Statement(s)
        target.startConstruction();
        //Assert statement(s)
        assertThat(target.isConstructing(), equalTo(Boolean.TRUE));
    }

    //Sapient generated method id: ${28013cdd-b521-384f-a2bb-6f2e5d85212e}, hash: DE26EFA9360DD0B6BB3AD7E932E207A3
    @Test()
    public void finishConstructionTest() {
        //Arrange Statement(s)
        ConstructionContext<Object> target = new ConstructionContext<>();
        //Act Statement(s)
        target.finishConstruction();
        //Assert statement(s)
        assertThat(target.isConstructing(), equalTo(Boolean.FALSE));
    }

    //Sapient generated method id: ${55d4b4e7-0019-356b-b29b-19c11b0be872}, hash: E622E7100DD2C222C169C3EC6E03DB04
    @Test()
    public void setProxyDelegatesWhenInvocationHandlersIsNull() {
        /* Branches:
         * (invocationHandlers != null) : false
         */
        //Arrange Statement(s)
        ConstructionContext<Object> target = new ConstructionContext<>();
        Object object = new Object();
        //Act Statement(s)
        target.setProxyDelegates(object);
    }
}
