<%@ taglib prefix="s" uri="/struts-tags" %>

<html>
  <body>
    <h1>Counter Example</h1>
    <h3><b>Hits in this session:</b>
      <s:property value="count"/></h3>

    <h3><b>Status:</b>
      <s:property value="status"/></h3>

    <h3><b>Message:</b>
      <s:property value="message"/></h3>
  </body>
</html>
