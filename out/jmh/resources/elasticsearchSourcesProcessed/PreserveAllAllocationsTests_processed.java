/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.inference.assignment.planning;

import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.ml.inference.assignment.planning.AssignmentPlan.Deployment;
import org.elasticsearch.xpack.ml.inference.assignment.planning.AssignmentPlan.Node;

import java.util.List;
import java.util.Map;

import static org.elasticsearch.test.hamcrest.OptionalMatchers.isEmpty;
import static org.elasticsearch.test.hamcrest.OptionalMatchers.isPresentWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class PreserveAllAllocationsTests extends ESTestCase {

    public void testGivenNoPreviousAssignments() {
        Node node1 = new Node("n_1", ByteSizeValue.ofMb(440).getBytes(), 4);
        Node node2 = new Node("n_2", ByteSizeValue.ofMb(440).getBytes(), 4);
        Deployment deployment1 = new Deployment("m_1", ByteSizeValue.ofMb(30).getBytes(), 2, 1, Map.of(), 0, 0, 0);
        Deployment deployment2 = new Deployment("m_2", ByteSizeValue.ofMb(30).getBytes(), 2, 4, Map.of(), 0, 0, 0);
        PreserveAllAllocations preserveAllAllocations = new PreserveAllAllocations(
            List.of(node1, node2),
            List.of(deployment1, deployment2)
        );
    }

    public void testGivenPreviousAssignments() {
        {
            Node node1 = new Node("n_1", ByteSizeValue.ofMb(640).getBytes(), 8);
            Node node2 = new Node("n_2", ByteSizeValue.ofMb(640).getBytes(), 8);
            Deployment deployment1 = new AssignmentPlan.Deployment(
                "m_1",
                ByteSizeValue.ofMb(30).getBytes(),
                2,
                1,
                Map.of("n_1", 1),
                1,
                0,
                0
            );
            Deployment deployment2 = new Deployment("m_2", ByteSizeValue.ofMb(50).getBytes(), 6, 4, Map.of("n_1", 1, "n_2", 2), 3, 0, 0);
            PreserveAllAllocations preserveAllAllocations = new PreserveAllAllocations(
                List.of(node1, node2),
                List.of(deployment1, deployment2)
            );

            List<Node> nodesPreservingAllocations = preserveAllAllocations.nodesPreservingAllocations();
            assertThat(nodesPreservingAllocations, hasSize(2));

            assertThat(nodesPreservingAllocations.get(0).id(), equalTo("n_1"));
            assertThat(nodesPreservingAllocations.get(0).availableMemoryBytes(), equalTo(0L));
            assertThat(nodesPreservingAllocations.get(0).cores(), equalTo(3));

            assertThat(nodesPreservingAllocations.get(1).id(), equalTo("n_2"));
            assertThat(nodesPreservingAllocations.get(1).availableMemoryBytes(), equalTo(ByteSizeValue.ofMb(300).getBytes()));
            assertThat(nodesPreservingAllocations.get(1).cores(), equalTo(0));

            List<AssignmentPlan.Deployment> modelsPreservingAllocations = preserveAllAllocations.modelsPreservingAllocations();
            assertThat(modelsPreservingAllocations, hasSize(2));

            assertThat(modelsPreservingAllocations.get(0).id(), equalTo("m_1"));
            assertThat(modelsPreservingAllocations.get(0).memoryBytes(), equalTo(ByteSizeValue.ofMb(30).getBytes()));
            assertThat(modelsPreservingAllocations.get(0).allocations(), equalTo(1));
            assertThat(modelsPreservingAllocations.get(0).threadsPerAllocation(), equalTo(1));
            assertThat(modelsPreservingAllocations.get(0).currentAllocationsByNodeId(), equalTo(Map.of("n_1", 0)));

            assertThat(modelsPreservingAllocations.get(1).id(), equalTo("m_2"));
            assertThat(modelsPreservingAllocations.get(1).memoryBytes(), equalTo(ByteSizeValue.ofMb(50).getBytes()));
            assertThat(modelsPreservingAllocations.get(1).allocations(), equalTo(3));
            assertThat(modelsPreservingAllocations.get(1).threadsPerAllocation(), equalTo(4));
            assertThat(modelsPreservingAllocations.get(1).currentAllocationsByNodeId(), equalTo(Map.of("n_1", 0, "n_2", 0)));

            AssignmentPlan plan = AssignmentPlan.builder(List.of(node1, node2), List.of(deployment1, deployment2))
                .assignModelToNode(deployment1, node1, 2)
                .build();
            assertThat(plan.assignments(deployment1), isPresentWith(Map.of(node1, 2)));
            assertThat(plan.assignments(deployment2), isEmpty());

            plan = preserveAllAllocations.mergePreservedAllocations(plan);
            assertThat(plan.assignments(deployment1), isPresentWith(Map.of(node1, 3)));
            assertThat(plan.assignments(deployment2), isPresentWith(Map.of(node1, 1, node2, 2)));

            assertThat(plan.getRemainingNodeMemory("n_1"), equalTo(0L));
            assertThat(plan.getRemainingNodeCores("n_1"), equalTo(1));
            assertThat(plan.getRemainingNodeMemory("n_2"), equalTo(ByteSizeValue.ofMb(300).getBytes()));
            assertThat(plan.getRemainingNodeCores("n_2"), equalTo(0));
        }
        {
            Node node1 = new Node("n_1", ByteSizeValue.ofMb(1000).getBytes(), 8);
            Node node2 = new Node("n_2", ByteSizeValue.ofMb(1000).getBytes(), 8);
            Deployment deployment1 = new AssignmentPlan.Deployment(
                "m_1",
                ByteSizeValue.ofMb(30).getBytes(),
                2,
                1,
                Map.of("n_1", 1),
                1,
                ByteSizeValue.ofMb(300).getBytes(),
                ByteSizeValue.ofMb(10).getBytes()
            );
            Deployment deployment2 = new Deployment(
                "m_2",
                ByteSizeValue.ofMb(50).getBytes(),
                6,
                4,
                Map.of("n_1", 1, "n_2", 2),
                3,
                ByteSizeValue.ofMb(300).getBytes(),
                ByteSizeValue.ofMb(10).getBytes()
            );
            PreserveAllAllocations preserveAllAllocations = new PreserveAllAllocations(
                List.of(node1, node2),
                List.of(deployment1, deployment2)
            );

            List<Node> nodesPreservingAllocations = preserveAllAllocations.nodesPreservingAllocations();
            assertThat(nodesPreservingAllocations, hasSize(2));

            assertThat(nodesPreservingAllocations.get(0).id(), equalTo("n_1"));
            assertThat(nodesPreservingAllocations.get(0).availableMemoryBytes(), equalTo(ByteSizeValue.ofMb(300).getBytes()));
            assertThat(nodesPreservingAllocations.get(0).cores(), equalTo(3));

            assertThat(nodesPreservingAllocations.get(1).id(), equalTo("n_2"));
            assertThat(nodesPreservingAllocations.get(1).availableMemoryBytes(), equalTo(ByteSizeValue.ofMb(630).getBytes()));
            assertThat(nodesPreservingAllocations.get(1).cores(), equalTo(0));

            List<AssignmentPlan.Deployment> modelsPreservingAllocations = preserveAllAllocations.modelsPreservingAllocations();
            assertThat(modelsPreservingAllocations, hasSize(2));

            assertThat(modelsPreservingAllocations.get(0).id(), equalTo("m_1"));
            assertThat(modelsPreservingAllocations.get(0).memoryBytes(), equalTo(ByteSizeValue.ofMb(30).getBytes()));
            assertThat(modelsPreservingAllocations.get(0).perDeploymentMemoryBytes(), equalTo(ByteSizeValue.ofMb(300).getBytes()));
            assertThat(modelsPreservingAllocations.get(0).perAllocationMemoryBytes(), equalTo(ByteSizeValue.ofMb(10).getBytes()));
            assertThat(modelsPreservingAllocations.get(0).allocations(), equalTo(1));
            assertThat(modelsPreservingAllocations.get(0).threadsPerAllocation(), equalTo(1));
            assertThat(modelsPreservingAllocations.get(0).currentAllocationsByNodeId(), equalTo(Map.of("n_1", 0)));

            assertThat(modelsPreservingAllocations.get(1).id(), equalTo("m_2"));
            assertThat(modelsPreservingAllocations.get(1).memoryBytes(), equalTo(ByteSizeValue.ofMb(50).getBytes()));
            assertThat(modelsPreservingAllocations.get(1).perDeploymentMemoryBytes(), equalTo(ByteSizeValue.ofMb(300).getBytes()));
            assertThat(modelsPreservingAllocations.get(1).perAllocationMemoryBytes(), equalTo(ByteSizeValue.ofMb(10).getBytes()));
            assertThat(modelsPreservingAllocations.get(1).allocations(), equalTo(3));
            assertThat(modelsPreservingAllocations.get(1).threadsPerAllocation(), equalTo(4));
            assertThat(modelsPreservingAllocations.get(1).currentAllocationsByNodeId(), equalTo(Map.of("n_1", 0, "n_2", 0)));

            AssignmentPlan plan = AssignmentPlan.builder(List.of(node1, node2), List.of(deployment1, deployment2))
                .assignModelToNode(deployment1, node1, 2)
                .build();
            assertThat(plan.assignments(deployment1), isPresentWith(Map.of(node1, 2)));
            assertThat(plan.assignments(deployment2), isEmpty());

            plan = preserveAllAllocations.mergePreservedAllocations(plan);
            assertThat(plan.assignments(deployment1), isPresentWith(Map.of(node1, 3)));
            assertThat(plan.assignments(deployment2), isPresentWith(Map.of(node1, 1, node2, 2)));

            assertThat(plan.getRemainingNodeMemory("n_1"), equalTo(ByteSizeValue.ofMb(280).getBytes()));
            assertThat(plan.getRemainingNodeCores("n_1"), equalTo(1));
            assertThat(plan.getRemainingNodeMemory("n_2"), equalTo(ByteSizeValue.ofMb(630).getBytes()));
            assertThat(plan.getRemainingNodeCores("n_2"), equalTo(0));
        }
    }

    public void testGivenModelWithPreviousAssignments_AndPlanToMergeHasNoAssignments() {
        Node node = new Node("n_1", ByteSizeValue.ofMb(400).getBytes(), 4);
        Deployment deployment = new Deployment("m_1", ByteSizeValue.ofMb(30).getBytes(), 2, 2, Map.of("n_1", 2), 2, 0, 0);
        PreserveAllAllocations preserveAllAllocations = new PreserveAllAllocations(List.of(node), List.of(deployment));

        AssignmentPlan plan = AssignmentPlan.builder(List.of(node), List.of(deployment)).build();
        assertThat(plan.assignments(deployment), isEmpty());

        plan = preserveAllAllocations.mergePreservedAllocations(plan);
        assertThat(plan.assignments(deployment), isPresentWith(Map.of(node, 2)));
        assertThat(plan.getRemainingNodeMemory("n_1"), equalTo(ByteSizeValue.ofMb(100).getBytes()));
        assertThat(plan.getRemainingNodeCores("n_1"), equalTo(0));
    }
}
