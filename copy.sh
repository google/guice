#!/bin/sh
# Copies classes into Guice's internal package.

client=/usr/local/google/clients/collect/google3

srcdir=core/src/com/google/inject/internal
testdir=core/test/com/google/inject/internal

filter() {
  sed 's/com.google.common.base.internal/com.google.inject.internal/' | \
  sed 's/com.google.common.base/com.google.inject.internal/' | \
  sed 's/com.google.common.collect/com.google.inject.internal/'
}

copy() {
  inFile=$1;
  fileName=`basename $inFile`
  dest=$2
  destpath=$dest/$fileName
  filter < $client/${inFile} > $destpath
}

commonpath=java/com/google/common

copy $commonpath/collect/ComputationException.java $srcdir
copy $commonpath/collect/AsynchronousComputationException.java $srcdir
copy $commonpath/collect/CustomConcurrentHashMap.java $srcdir
copy $commonpath/collect/ExpirationTimer.java $srcdir
copy $commonpath/collect/MapMaker.java $srcdir
copy $commonpath/collect/NullOutputException.java $srcdir
copy $commonpath/base/Function.java $srcdir
copy $commonpath/base/Nullable.java $srcdir
copy $commonpath/base/FinalizableReference.java $srcdir
copy $commonpath/base/FinalizableReferenceQueue.java $srcdir
copy $commonpath/base/internal/Finalizer.java $srcdir
copy $commonpath/base/FinalizableWeakReference.java $srcdir
copy $commonpath/base/FinalizableSoftReference.java $srcdir
copy $commonpath/base/FinalizablePhantomReference.java $srcdir

commontestspath=javatests/com/google/common

copy $commontestspath/base/FinalizableReferenceQueueTest.java $testdir
copy $commontestspath/collect/MapMakerTestSuite.java $testdir
copy $commontestspath/collect/Jsr166HashMap.java $testdir
copy $commontestspath/collect/Jsr166HashMapTest.java $testdir
copy $commonpath/collect/ForwardingConcurrentMap.java $testdir
copy $commonpath/collect/ForwardingMap.java $testdir
copy $commonpath/collect/ForwardingCollection.java $testdir
copy $commonpath/collect/ForwardingObject.java $testdir
copy $commonpath/collect/ForwardingSet.java $testdir
copy $commonpath/collect/ForwardingMap.java $testdir
copy $commonpath/base/Preconditions.java $testdir

chmod +w -R $srcdir $testdir
