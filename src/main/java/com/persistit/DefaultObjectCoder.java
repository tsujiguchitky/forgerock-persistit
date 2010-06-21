/*
 * Copyright (c) 2004 Persistit Corporation. All Rights Reserved.
 *
 * The Java source code is the confidential and proprietary information
 * of Persistit Corporation ("Confidential Information"). You shall
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Persistit Corporation.
 *
 * PERSISTIT CORPORATION MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
 * A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. PERSISTIT CORPORATION SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * Created on Oct 18, 2004
 */
package com.persistit;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;

import com.persistit.encoding.CoderContext;
import com.persistit.encoding.CoderManager;
import com.persistit.encoding.KeyRenderer;
import com.persistit.encoding.ObjectCoder;
import com.persistit.encoding.ValueCoder;
import com.persistit.exception.ConversionException;

/**
 * <p>
 * Implements {@link ObjectCoder} using reflection to access the properties
 * and/or fields of an object. An <tt>ObjectCoder</tt> provides methods to
 * encode and decode properties or fields of an object into or from {@link Key
 * Key}s and {@link Value Value}s.
 * </p>
 * <p>
 * <tt>ObjectCoder</tt>s allow Persistit to store and retrieve arbitrary objects
 * - even non-Serializable objects - without byte-code enhancement, without
 * incurring the space or time overhead of Java serialization or the need to
 * modify the class to perform custom serialization. During initialization, an
 * application typically associates an <tt>ObjectCoder</tt> with the
 * <tt>Class</tt> of each object that will be stored in or fetched from the
 * Persistit database. An <tt>ObjectCoder</tt> implements all of the logic
 * necessary to encode and decode the state of any object of that class to and
 * from Persistit storage structures.
 * </p>
 * <p>
 * A <tt>DefaultObjectCoder</tt> is a generic implementation of the
 * <tt>ObjectCoder</tt> interface. During initialization an application uses the
 * static {@link #registerObjectCoder registerObjectCoder} or
 * {@link #registerObjectCoderFromBean registerObjectCoderFromBean} method to
 * construct and register an <tt>ObjectCoder</tt> for a particular class with
 * the current {@link CoderManager}. To define a <tt>DefaultObjectCoder</tt> for
 * a class, all that is required is two arrays, one containing names of
 * properties or fields that will be used in constructing <tt>Key</tt> values
 * under which instances of the class will be stored, and the other containing
 * names of properties or fields that will be encoded in the <tt>Value</tt>
 * associated with that key.
 * </p>
 * <p>
 * Unlike {@link DefaultValueCoder}, which implements default serialization for
 * Persistit version 1.1, this extended implementation can encode and decode
 * non-serializable objects (that is, instances of classes that are do not
 * implement <tt>java.io.Serializable</tt>). However, classes handled by this
 * <tt>ObjectCoder</tt> must have a no-argument constructor which is used to
 * construct new objects instances in the
 * {@link ValueCoder#get(Value, Class, CoderContext)} method. An extension of
 * this class may override the {@link #newInstance()} method to provide
 * customized logic for constructing new instances of the client class.
 * </p>
 * <p>
 * <tt>DefaultObjectCoder</tt> may be used to serialize and deserialize the
 * private fields of an object through reflection. If the application using
 * Persistit is running in the context of a <tt>SecurityManager</tt>, the
 * permission <tt>ReflectPermission("suppressAccessChecks")</tt> is required to
 * permit this access. See the JDK documentation for
 * <tt>java.lang.reflect.AccessibleObject</tt> and <a
 * href="../../../../Object_Serialization_Notes.html"> Persistit JSA 1.1 Object
 * Serialization</a> for details. Similarly, the same permission is required
 * when deserializing an object with a private constructor.
 * </p>
 * <p>
 * For Java Runtime Environments 1.3 through 1.4.2, this class is unable to
 * deserialize fields marked <tt>final</tt> due to a bug in the JRE (see <a
 * href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5044412"> bug
 * 5044412</a>). This bug was fixed in Java SE 5.0.
 * </p>
 * <p>
 * The following code fragment defines a simple class, registers a
 * <tt>DefaultObjectCoder</tt> for it, then provides methods for storing and and
 * fetching instances to and from a Persistit database:
 * 
 * <pre>
 * <code>
 * 
 *      static class Vehicle
 *      {
 *          String id;
 *          String description;
 *          int speed;
 *          int wheels;
 *          int passengers
 *          boolean canFly;
 *      }
 * 
 *      static
 *      {
 *          DefaultObjectCoder.registerObjectCoder(
 *              Vehicle.class,
 *              {"id"},
 *              {"description", "speed", "wheels", "passengers", "canFly"});
 *      }
 * 
 *      void storeVehicle(Vehicle v, Exchange exchange)
 *      throws PersistitException
 *      {
 *          exchange.getValue().put(v);
 *          exchange.clear().append(v).store();
 *      }
 * 
 *      Vehicle getVehicle(String id, Exchange exchange)
 *      throws PersistitException
 *      {
 *          Vehicle v = new Vehicle();
 *          v.id = id;
 *          //
 *          // Using the id field as primary key, fetch the remaining fields
 *          // of the object.
 *          //
 *          exchange.clear().append(v).fetch().get(v);
 *          return v;
 *      }
 * </code>
 * </pre>
 * 
 * </p>
 * 
 * @version 1.1
 */
public class DefaultObjectCoder extends DefaultValueCoder implements
        KeyRenderer {

    private Builder _keyBuilder;

    /**
     * Map of keyName : Accessor[]
     */
    private HashMap _secondaryKeyTupleMap;
    private ArrayList _secondaryKeyTupleList;

    private DefaultObjectCoder(Persistit persistit, Class clientClass,
            Builder valueBuilder) {
        super(persistit, clientClass, valueBuilder);
    }

    /**
     * <p>
     * Convenience method that creates and registers a
     * <tt>DefaultObjectCoder</tt> for a Java Bean.
     * </p>
     * <p>
     * The supplied <tt>Class</tt> must conform to the requirements of a Java
     * bean; in particular it must have a no-argument constructor. The resulting
     * ObjectCoder will serialize and deserialize the properties of this bean as
     * determined by the BeanInfo derived from introspecting the bean's class or
     * its associated BeanInfo class.
     * </p>
     * <p>
     * The <tt>keyPropertyNames</tt> array specifies names of one or more
     * properties of the bean that are to be concatenated, in the order
     * specified by the array, to form the primary key under which instances of
     * this bean will be stored.
     * </p>
     * <p>
     * Persistit must be initialized at the time this method is called. This
     * method registers the newly created <tt>DefaultObjectCoder</tt> the
     * Persistit instance's current <tt>CoderManager</tt>.
     * </p>
     * 
     * @param clientClass
     *            The <tt>Class</tt> of object this <tt>DefaultObjectCoder</tt>
     *            will encode and decode
     * 
     * @param keyPropertyNames
     *            Array of names of properties that constitute the primary key
     *            of stored instances
     * 
     * @throws IntrospectionException
     */
    public synchronized static void registerObjectCoderFromBean(
            Persistit persistit, Class clientClass, String[] keyPropertyNames)
            throws IntrospectionException {
        BeanInfo beanInfo = Introspector.getBeanInfo(clientClass);
        PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
        boolean isKeyProperty[] = new boolean[descriptors.length];

        for (int index = 0; index < keyPropertyNames.length; index++) {
            String name = keyPropertyNames[index];
            boolean found = false;

            for (int j = 0; !found && j < descriptors.length; j++) {
                if (descriptors[j].getName().equals(name)) {
                    isKeyProperty[j] = true;
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Bean for class "
                        + clientClass.getName() + " has no property named "
                        + name);
            }
        }

        int count = 0;
        String[] valuePropertyNames = new String[descriptors.length
                - keyPropertyNames.length];
        for (int j = 0; j < descriptors.length; j++) {
            if (!isKeyProperty[j]) {
                valuePropertyNames[count] = descriptors[j].getName();
                count++;
            }
        }
        Builder valueBuilder = new Builder("value", valuePropertyNames,
                clientClass);

        DefaultObjectCoder coder = new DefaultObjectCoder(persistit,
                clientClass, valueBuilder);

        CoderManager cm = null;
        cm = persistit.getCoderManager();
        cm.registerKeyCoder(clientClass, coder);
        cm.registerValueCoder(clientClass, coder);
    }

    /**
     * <p>
     * Convenience method that creates and registers a
     * <tt>DefaultObjectCoder</tt> for an arbitrary Java class. The class is not
     * required to be serializable (i.e., to implement
     * <tt>java.io.Serializable</tt>), but it must have a no-argument
     * constructor. If the class implements <tt>Externalizable</tt>, then the
     * constructor is required to be public, thus conforming to the contract for
     * <tt>Externalizable</tt> classes. Otherwise, the constructor may be
     * public, protected, package-private or private.
     * </p>
     * <p>
     * The supplied <tt>Class</tt> and the supplied names for fields or
     * properties constitute the state to be recorded in Persistit. The
     * resulting coder is capable of efficiently serializing and deserializing
     * instances of the client <tt>Class</tt> in Persistit records.
     * </p>
     * <p>
     * Two String arrays determine the structure of the stored data. Each
     * element in these arrays identifies the name of either a field or a
     * property of the client <tt>Class</tt>. For each name xyz, this
     * constructor first searches for a method with a signature compatible with
     * either
     * 
     * <pre>
     * <code>
     *    <i>Type</i>getXyz()
     * </code>or<code> boolean isXyz()
     * </code>
     * </pre>
     * 
     * and a method with a signature compatible with
     * 
     * <pre>
     * <code>
     *    void setXyz(<i>Type</i> value)
     * </code>
     * </pre>
     * 
     * In the case of the boolean property named <code>isXyz</code>, the value
     * <tt><i>Type</i></tt> must be <tt>boolean</tt>; otherwise, the type of the
     * value specified by the setter must be assignable from return type of the
     * getter. In other words, the setXyz method must accept any value that the
     * getXyz method might return. If multiple setXyz methods meet this
     * requirement, the method with the most specific argument type is selected.
     * </p>
     * <p>
     * If both setXyz and either getXyz or isXyz methods meeting these criteria
     * are found then the accessor is a property accessor, and will be stored
     * and retrieved from the object using these methods. Otherwise, the
     * accessor name must be the name of an accessible field of the client
     * <tt>Class</tt>. Non-public fields are accessible only if permitted by the
     * security policy in which the code is executed and only on JDK versions
     * 1.2 and above. (See the JDK documentation for
     * <tt>java.lang.reflect.AccessibleObject</tt> for details.)
     * </p>
     * <p>
     * The <tt>keyAccesssorNames</tt> array identifies the properties or fields
     * whose values will constitute the primary key of an object stored with
     * this <tt>objectCoder</tt>; the <tt>valueAccessorNames</tt> array
     * identifies the properties or fields that will constitute the value
     * associated with that key.
     * </p>
     * <p>
     * Persistit must be initialized at the time this method is called. This
     * method registers the newly created <tt>DefaultObjectCoder</tt> the
     * Persistit instance's current <tt>CoderManager</tt>.
     * </p>
     * 
     * @param clientClass
     *            The <tt>Class</tt> whose instances are to be encoded and
     *            decoded
     * 
     * @param keyAccessorNames
     *            Array of names of properties that constitute the primary key
     *            of stored instances of the <tt>clientClass</tt>.
     * 
     * @param valueAccessorNames
     *            Array of names of properties that constitute the value of
     *            stored instances of the <tt>clientClass</tt>.
     */
    public synchronized static void registerObjectCoder(Persistit persistit,
            Class clientClass, String[] keyAccessorNames,
            String[] valueAccessorNames) {
        Builder keyBuilder = new Builder("primaryKey", keyAccessorNames,
                clientClass);

        Builder valueBuilder = new Builder("value", valueAccessorNames,
                clientClass);

        DefaultObjectCoder coder = new DefaultObjectCoder(persistit,
                clientClass, valueBuilder);

        coder._keyBuilder = keyBuilder;

        CoderManager cm = null;
        cm = persistit.getCoderManager();
        cm.registerKeyCoder(clientClass, coder);
        cm.registerValueCoder(clientClass, coder);
    }

    /**
     * Construct a new instance of the client class. By default this uses a
     * no-argument constructor declared by the class, and is equivalent to
     * {@link Class#newInstance()}. Subclasses may override this method to
     * provide custom logic for constructing new instances.
     * 
     * @return a new instance of the Class for which this
     *         <tt>DefaultObjectCoder</tt> is registered.
     */
    @Override
    protected Object newInstance() {
        return super.newInstance();
    }

    /**
     * Construct and add a secondary index <tt>Builder</tt>.
     * 
     * @param name
     *            Name of the secondary index
     * @param keyAccessorNames
     *            The property and/or field names
     * @return The newly constructed Builder
     */
    public synchronized Builder addSecondaryIndexBuilder(String name,
            String[] keyAccessorNames) {
        Builder builder = new Builder(name, keyAccessorNames, getClientClass());
        if (_secondaryKeyTupleMap == null) {
            _secondaryKeyTupleMap = new HashMap();
            _secondaryKeyTupleList = new ArrayList();
        }
        Builder oldBuilder = (Builder) _secondaryKeyTupleMap.put(name, builder);
        if (oldBuilder != null)
            _secondaryKeyTupleList.remove(oldBuilder);
        _secondaryKeyTupleList.add(builder);
        return builder;
    }

    /**
     * Remove a secondary index <tt>Builder</tt> specified by its name.
     * 
     * @param name
     *            Name if the secondary index to remove.
     * @return The Builder that was removed, or <tt>null</tt> if there was none.
     */
    public synchronized Builder removeSecondaryIndexBuilder(String name) {
        if (_secondaryKeyTupleMap == null)
            return null;
        Builder builder = (Builder) _secondaryKeyTupleMap.get(name);
        if (builder != null) {
            _secondaryKeyTupleList.remove(builder);
            _secondaryKeyTupleMap.remove(name);
        }
        return builder;
    }

    /**
     * Return a <tt>Builder</tt>s by index, according to the order in which
     * secondary index builders were added.
     * 
     * @return The Builder
     */
    public synchronized Builder getSecondaryIndexBuilder(int index) {
        if (_secondaryKeyTupleList != null && index >= 0
                && index < _secondaryKeyTupleList.size()) {
            return (Builder) _secondaryKeyTupleList.get(index);
        }
        throw new IndexOutOfBoundsException("No such secondary index: " + index);
    }

    /**
     * Return a <tt>Builder</tt> by name.
     * 
     * @return The Builder, or <tt>null</tt> if there is no secondary index with
     *         the specified name.
     */
    public synchronized Builder getSecondaryIndexBuilder(String name) {
        if (_secondaryKeyTupleMap == null)
            return null;
        return (Builder) _secondaryKeyTupleMap.get(name);
    }

    /**
     * Return the count of secondary index builders.
     * 
     * @return The count
     */
    public synchronized int getSecondaryIndexBuilderCount() {
        if (_secondaryKeyTupleList == null)
            return 0;
        else
            return _secondaryKeyTupleList.size();
    }

    /**
     * Return the <tt>Builder</tt> that copies data values between a
     * <tt>Key</tt> and a client object. The resulting <tt>Key</tt> value is
     * intended to serve as the primary key for the object.
     * 
     * @return The Builder
     */
    public Builder getKeyBuilder() {
        return _keyBuilder;
    }

    /**
     * <p>
     * Append a key segment derived from an object to a {@link Key}. This method
     * will be called only if this <tt>KeyCoder</tt> has been registered with
     * the current {@link CoderManager} to encode objects having the class of
     * the supplied object.
     * </p>
     * <p>
     * Upon completion of this method, the backing byte array of the
     * <tt>Key</tt> and its size should be updated to reflect the appended key
     * segment. Use the methods {@link Key#getEncodedBytes},
     * {@link Key#getEncodedSize} and {@link Key#setEncodedSize} to manipulate
     * the byte array directly. More commonly, the implementation of this method
     * will simply append appropriate identifying fields within the object to
     * the key.
     * </p>
     * 
     * @param key
     *            The {@link Key} to append to
     * @param object
     *            The object to encode
     * @param context
     *            An arbitrary object that can optionally be supplied by the
     *            application to convey an application-specific context for the
     *            operation. (See {@link CoderContext}.) The default value is
     *            <tt>null</tt>.
     */
    public void appendKeySegment(Key key, Object object, CoderContext context)
            throws ConversionException {
        Accessor accessor = null;
        checkKeyAccessors();
        Builder keyBuilder = getKeyBuilder(context);
        try {
            int count = keyBuilder.getSize();
            for (int index = 0; index < count; index++) {
                accessor = keyBuilder.getAccessor(index);
                accessor.toKey(object, key);
            }
        } catch (Exception e) {
            throw new ConversionException("Encoding " + accessor.toString()
                    + " for " + getClientClass(), e);
        }
    }

    /**
     * <p>
     * Decode a key segment as an Object value. This method is invoked when
     * {@link Key#decode(Object)} attempts to decode a key segment that was
     * previously append by the {@link #appendKeySegment} method. Thus the
     * implementation of this method must precisely decode the same bytes that
     * were generated by the companion <tt>appendKeySegment</tt> method of this
     * <tt>ValueCoder</tt>.
     * </p>
     * <p>
     * This method will be called only if this <tt>KeyCoder</tt> is registered
     * with the current {@link CoderManager} to encode and decode objects of the
     * class that is actually represented in the <tt>Key</tt>. The class with
     * which the key was encoded is provided as an argument. This permits one
     * <tt>KeyCoder</tt> to handle multiple classes because the implementation
     * can dispatch to code appropriate for the particular supplied class. The
     * implementation should construct and return an Object having the same
     * class as the supplied class.
     * </p>
     * 
     * @param key
     *            The {@link Key} from which data should be decoded
     * @param clazz
     *            The expected <tt>Class</tt> of the returned object
     * @param context
     *            An arbitrary object that can optionally be supplied by the
     *            application to convey an application-specific context for the
     *            operation. (See {@link CoderContext}.) The default value is
     *            <tt>null</tt>.
     * @throws ConversionException
     */

    public Object decodeKeySegment(Key key, Class clazz, CoderContext context)
            throws ConversionException {
        if (clazz != getClientClass())
            throw new ClassCastException("Client class "
                    + getClientClass().getName()
                    + " does not match requested class " + clazz.getName());
        Object instance;
        try {
            instance = getClientClass().newInstance();
        } catch (Exception e) {
            throw new ConversionException(
                    "Unable to instantiate an instance of " + getClientClass(),
                    e);

        }
        renderKeySegment(key, instance, clazz, context);

        return readResolve(instance);
    }

    /**
     * <p>
     * Populates the state of the supplied target <tt>Object</tt> by decoding
     * the next key segment of the supplied <tt>Key</tt>. This method will be
     * called only if this <tt>KeyRenderer</tt> has been registered with the
     * current {@link CoderManager} to encode objects having the supplied
     * <tt>Class</tt> value. In addition, Persistit will never call this method
     * to decode a value that was <tt>null</tt> when written because null values
     * are handled by built-in encoding logic.
     * </p>
     * 
     * @param key
     *            The <tt>Key</tt> from which interior fields of the object are
     *            to be retrieved
     * 
     * @param target
     *            An object into which the key segment is to be written
     * 
     * @param clazz
     *            The class of the object that was originally encoded into
     *            Value.
     * 
     * @param context
     *            An arbitrary object that can optionally be supplied by the
     *            application to convey an application-specific context for the
     *            operation. (See {@link CoderContext}.) The default value is
     *            <tt>null</tt>.
     * 
     * @throws ConversionException
     */
    public void renderKeySegment(Key key, Object target, Class clazz,
            CoderContext context) throws ConversionException {
        if (clazz != getClientClass())
            throw new ClassCastException("Client class "
                    + getClientClass().getName()
                    + " does not match requested class " + clazz.getName());

        checkKeyAccessors();
        Builder keyBuilder = getKeyBuilder(context);
        int count = keyBuilder.getSize();
        Accessor accessor = null;
        try {
            for (int index = 0; index < count; index++) {
                accessor = _keyBuilder.getAccessor(index);
                accessor.fromKey(target, key);
            }
        } catch (Exception e) {
            throw new ConversionException("Decoding " + accessor.toString()
                    + " for " + getClientClass(), e);
        }
    }

    private Builder getKeyBuilder(CoderContext context) {
        if (context == null || context == _keyBuilder)
            return _keyBuilder;

        if (_secondaryKeyTupleList != null) {
            int count = getSecondaryIndexBuilderCount();
            for (int index = 0; index < count; index++) {
                if (context == _secondaryKeyTupleList.get(index)) {
                    return (Builder) context;
                }
            }
        }
        throw new ConversionException("No such Builder " + context);
    }

    private void checkKeyAccessors() {
        if (_keyBuilder.getSize() == 0) {
            throw new ConversionException("ObjectCoder for class "
                    + getClientClass().getName()
                    + " has no Key fields or properties");
        }
    }

    /**
     * Return a String description of this DefaultObjectCoder. The String
     * includes the client class name, and the property or field names
     * identifying the properties and/or fields this coder accesses and
     * modifies.
     * 
     * @return A String description.
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("DefaultObjectCoder(");
        sb.append(getClientClass().getName());
        sb.append(",");
        sb.append(_keyBuilder.toString());
        sb.append(",");
        sb.append(getValueBuilder().toString());
        if (_secondaryKeyTupleList != null) {
            for (int index = 0; index < _secondaryKeyTupleList.size(); index++) {
                sb.append(",");
                sb.append(_secondaryKeyTupleList.get(index));
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
