java.lang.String a = "foo";
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(a)){
print(a);
}
 else {
print("foo foo");
java.util.ArrayList<java.lang.Integer> list = new java.util.ArrayList<java.lang.Integer>(Arrays.asList(1, 2, 3));
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(list)?"full: " + java.lang.String.valueOf(list):"empty");
}

