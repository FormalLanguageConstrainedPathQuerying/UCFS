import os
import subprocess
import sys

gradlew = "./gradlew"
task = ":test-shared:test"

test_name = ""  # example "solver.correctnessTests.AmbiguousAStar3GrammarTest.AmbiguousAStar3GrammarTreeCorrectnessTest"
test_case_name = "" # example "small_test"
def run_test_for_mem(test_name):
    def get_cmd(mem):
        return [
            gradlew,
            task,
            f"-DtestMaxHeapSize={mem}m",
            "--tests", test_name,
            f"-Dspecial_case={test_case_name}",
            f"-Dcount_for_case=1",
            f"-Dwrite_case_time=0"
        ]

    cache = {}

    def execute(mem):
        if mem in cache:
            return cache[mem]

        cmd = get_cmd(mem)
        try:
            process = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,timeout=60)
            return_code = process.returncode
        except:
            return_code = 1
        cache[mem] = return_code
        return return_code

    l = 1
    r = 64
    max_mem = 4*1024
    while r <= max_mem:
        return_code = execute(r)
        print(r)
        if return_code != 0:
            l = r
            r *= 2
        else:
            break
    if r == 2*max_mem:
        return r

    while l < r - 1:
        m = (l + r) // 2
        print(m)
        return_code = execute(m)

        if return_code != 0:
            l = m
        else:
            r = m

    return r


with open("tests_list.conf", "r") as input:
    with open(f"res.txt", "w") as output:
        output.write(f"test,case,mem\n")
        for line in input:
            config = line.split()
            test_name = config[0]
            test_case_name = config[1]
            print(test_name)
            print(test_case_name)
            mem = run_test_for_mem(test_name)
            cmd = [
                  gradlew,
                  task,
                  f"-DtestMaxHeapSize=15m",
                  "--tests", test_name,
                  f"-Dspecial_case={test_case_name}",
                  f"-Dcount_for_case=1"
              ]
            process = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return_code = process.returncode
            print(f"Got for test = {test_name}: {mem}mb")
            output.write(f"{test_name},{test_case_name},{mem}\n")
