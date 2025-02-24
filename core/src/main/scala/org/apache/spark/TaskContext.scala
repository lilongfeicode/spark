/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark

import java.io.Serializable
import java.util.Properties

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.executor.TaskMetrics
import org.apache.spark.memory.TaskMemoryManager
import org.apache.spark.metrics.source.Source
import org.apache.spark.util.{AccumulatorV2, TaskCompletionListener, TaskFailureListener}


object TaskContext {
  /**
   * Return the currently active TaskContext. This can be called inside of
   * user functions to access contextual information about running tasks.
   */
  def get(): TaskContext = taskContext.get

  /**
   * Returns the partition id of currently active TaskContext. It will return 0
   * if there is no active TaskContext for cases like local execution.
   */
  def getPartitionId(): Int = {
    val tc = taskContext.get()
    if (tc eq null) {
      0
    } else {
      tc.partitionId()
    }
  }

  private[this] val taskContext: ThreadLocal[TaskContext] = new ThreadLocal[TaskContext]

  // Note: protected[spark] instead of private[spark] to prevent the following two from
  // showing up in JavaDoc.
  /**
   * Set the thread local TaskContext. Internal to Spark.
   */
  protected[spark] def setTaskContext(tc: TaskContext): Unit = taskContext.set(tc)

  /**
   * Unset the thread local TaskContext. Internal to Spark.
   */
  protected[spark] def unset(): Unit = taskContext.remove()

  /**
   * An empty task context that does not represent an actual task.  This is only used in tests.
   */
  private[spark] def empty(): TaskContextImpl = {
    new TaskContextImpl(0, 0, 0, 0, 0, null, new Properties, null)
  }
}


/**
 * Contextual information about a task which can be read or mutated during
 * execution. To access the TaskContext for a running task, use:
 * {{{
 *   org.apache.spark.TaskContext.get()
 * }}}
 */
abstract class TaskContext extends Serializable {
  // Note: TaskContext must NOT define a get method. Otherwise it will prevent the Scala compiler
  // from generating a static get method (based on the companion object's get method).

  // Note: Update JavaTaskContextCompileCheck when new methods are added to this class.

  // Note: getters in this class are defined with parentheses to maintain backward compatibility.

  /**
   * Returns true if the task has completed.
   */
  def isCompleted(): Boolean

  /**
   * Returns true if the task has been killed.
   */
  def isInterrupted(): Boolean

  /**
   * Returns true if the task is running locally in the driver program.
   * @return false
   */
  @deprecated("Local execution was removed, so this always returns false", "2.0.0")
  def isRunningLocally(): Boolean

  /**
   * Adds a (Java friendly) listener to be executed on task completion.
   * This will be called in all situation - success, failure, or cancellation.
   * An example use is for HadoopRDD to register a callback to close the input stream.
   *
   * Exceptions thrown by the listener will result in failure of the task.
   */
  def addTaskCompletionListener(listener: TaskCompletionListener): TaskContext

  /**
   * Adds a listener in the form of a Scala closure to be executed on task completion.
   * This will be called in all situations - success, failure, or cancellation.
   * An example use is for HadoopRDD to register a callback to close the input stream.
   *
   * Exceptions thrown by the listener will result in failure of the task.
   */
  def addTaskCompletionListener(f: (TaskContext) => Unit): TaskContext = {
    addTaskCompletionListener(new TaskCompletionListener {
      override def onTaskCompletion(context: TaskContext): Unit = f(context)
    })
  }

  /**
   * Adds a listener to be executed on task failure.
   * Operations defined here must be idempotent, as `onTaskFailure` can be called multiple times.
   */
  def addTaskFailureListener(listener: TaskFailureListener): TaskContext

  /**
   * Adds a listener to be executed on task failure.
   * Operations defined here must be idempotent, as `onTaskFailure` can be called multiple times.
   */
  def addTaskFailureListener(f: (TaskContext, Throwable) => Unit): TaskContext = {
    addTaskFailureListener(new TaskFailureListener {
      override def onTaskFailure(context: TaskContext, error: Throwable): Unit = f(context, error)
    })
  }

  /**
   * The ID of the stage that this task belong to.
   */
  def stageId(): Int

  /**
   * How many times the stage that this task belongs to has been attempted. The first stage attempt
   * will be assigned stageAttemptNumber = 0, and subsequent attempts will have increasing attempt
   * numbers.
   */
  def stageAttemptNumber(): Int

  /**
   * The ID of the RDD partition that is computed by this task.
   */
  def partitionId(): Int

  /**
   * How many times this task has been attempted.  The first task attempt will be assigned
   * attemptNumber = 0, and subsequent attempts will have increasing attempt numbers.
   */
  def attemptNumber(): Int

  /**
   * An ID that is unique to this task attempt (within the same SparkContext, no two task attempts
   * will share the same attempt ID).  This is roughly equivalent to Hadoop's TaskAttemptID.
   */
  def taskAttemptId(): Long

  /**
   * Get a local property set upstream in the driver, or null if it is missing. See also
   * `org.apache.spark.SparkContext.setLocalProperty`.
   */
  def getLocalProperty(key: String): String

  @DeveloperApi
  def taskMetrics(): TaskMetrics

  /**
   * ::DeveloperApi::
   * Returns all metrics sources with the given name which are associated with the instance
   * which runs the task. For more information see `org.apache.spark.metrics.MetricsSystem`.
   */
  @DeveloperApi
  def getMetricsSources(sourceName: String): Seq[Source]

  /**
   * Returns the manager for this task's managed memory.
   */
  private[spark] def taskMemoryManager(): TaskMemoryManager

  /**
   * Register an accumulator that belongs to this task. Accumulators must call this method when
   * deserializing in executors.
   */
  private[spark] def registerAccumulator(a: AccumulatorV2[_, _]): Unit

}
