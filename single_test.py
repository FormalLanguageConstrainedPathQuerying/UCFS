import os
import subprocess
import sys

gradlew = "./gradlew"
task = ":test-shared:test"

test_name = sys.argv[1]  # example "solver.correctnessTests.AmbiguousAStar3GrammarTest.AmbiguousAStar3GrammarTreeCorrectnessTest"
test_case_name = sys.argv[2] # example "small_test"
def run_test_for_mem(test_name):
    def get_cmd(mem):
        return [
            gradlew,
            task,
            f"-DtestMaxHeapSize={mem}m",
            "--tests", test_name,
            f"-Dspecial_case = {test_case_name}",
            f"-Dcount_for_case=1"
        ]

    cache = {}

    def execute(mem):
        if mem in cache:
            return cache[mem]

        cmd = get_cmd(mem)
        process = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return_code = process.returncode
        cache[mem] = return_code
        print(return_code)
        return return_code

    l = 1
    r = 64
    max_mem = 4*1024
    while r <= max_mem:
        return_code = execute(r)
        if return_code != 0:
            l = r
            r *= 2
        else:
            break
    print(f"calculate r = {r}")
    if r == 2*max_mem:
        return r

    while l < r - 1:
        m = (l + r) // 2
        print(m)
        return_code = execute(m)
        print(f"for {m} mem got code {return_code}")

        if return_code != 0:
            l = m
        else:
            r = m

    return r

print(test_name)
mem = run_test_for_mem(test_name)
print(f"Got for test = {test_name}: {mem}mb")
with open(f"{test_name.replace('.', '_')}_res.txt", "w") as output:
    output.write(f"test,mem\n")
    output.write(f"{test_name},{mem}\n")
