/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.struts2.example;

import static com.opensymphony.xwork2.Action.SUCCESS;

import com.google.inject.Inject;

public class Count {

  final Counter counter;
  final Service service;
  String message;

  @Inject
  public Count(Counter counter, Service service) {
    this.counter = counter;
    this.service = service;
  }

  public String execute() {
    return SUCCESS;
  }

  public int getCount() {
    return counter.increment();
  }

  public String getStatus() {
    return service.getStatus();
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
