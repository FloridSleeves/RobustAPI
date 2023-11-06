import pandas as pd
import numpy as np
import argparse, os, time, json, copy
from typing import List, Dict
import random, multiprocessing, sys, openai, subprocess, fire, tqdm, glob


class Counter:
    def __init__(self):
        self.total_count = 0
        self.vio_ast_count = 0
        self.pass_count = 0
        self.parsefail_count = 0
        self.hash = ".tmp" + str(random.randint(0, 1000000))

    def print(self, prefix=""):
        pass_count = self.total_count - self.vio_ast_count - self.parsefail_count
        print(
            "TOTAL: %d, PASS:%d, VIO: %d, PARSEFAIL: %d"
            % (self.total_count, pass_count, self.vio_ast_count, self.parsefail_count)
        )
        exec_count = pass_count + self.vio_ast_count
        misuse_rate = 0 if exec_count == 0 else self.vio_ast_count / exec_count
        print(
            "Misuse: %f %f %f"
            % (
                misuse_rate,
                (exec_count / self.total_count),
                (self.vio_ast_count / self.total_count),
            )
        )

    def check(self, code_segments, pattern_path, passk=1):
        if passk > len(code_segments):
            print("Pass@k is larger than the number of responses!")
            return
        exec_flag = False
        pass_flag = False
        k = 0
        for cs in code_segments:
            # Write the code segment to a file for analysis
            fseg = open(self.hash, "w")
            fseg.write(cs)
            fseg.close()
            # Check the pattern by invoke shell
            result = subprocess.run(
                [
                    "java -cp ./eval/APIChecker-0.0.1-SNAPSHOT.jar checker.maple.server.Evaluator "
                    + self.hash
                    + " "
                    + pattern_path
                ],
                stdout=subprocess.PIPE,
                shell=True,
            ).stdout.decode("utf-8")
            if result.find("VIOLATION:") != -1:
                exec_flag = True
            elif result.find("PARSEFAIL") != -1 or result.find("NOCODE") != -1:
                pass
            else:
                # pass
                exec_flag = True
                pass_flag = True
            os.remove(self.hash)
            # print(exec_flag, pass_flag, cs.replace('\n', ' '))
            k += 1
            if k == passk:
                break
        if exec_flag and pass_flag:
            self.pass_count += 1
        elif exec_flag and not pass_flag:
            self.vio_ast_count += 1
        elif not exec_flag:
            self.parsefail_count += 1


def loadDataset(dataset_path):
    # read each item: url, api, title, questions
    f = open(dataset_path)
    dataset = []
    for l in f:
        dataset.append(json.loads(l))
    f.close()
    return dataset


def matchBracket(code):
    # add bracket if the brackes in code are not matched
    stack = []
    code_res = code
    for i in range(len(code)):
        if code[i] == "{":
            stack.append("{")
        elif code[i] == "}":
            if len(stack) == 0:
                code_res = "{" + code_res
            else:
                stack.pop()
    if len(stack) > 0:
        code_res += "}" * len(stack)
    return code_res


def findAPI(api, code):
    # split api into tokens
    api_tokens = api.split(".")
    for token in [api_tokens[-1]]:
        if token == "":
            continue
        if code.find(token) == -1:
            return False
    return True


def getCodeSegment(text, model):
    try:
        # DeepSeek will repeat the prompt first, so we truncate the prompt
        api = text.split("api>>>:")[1].split("<<<code>>>:")[0].strip()
        tmp = text.split("<<<code>>>:")
        if model == "polycoder":
            idx1 = float("inf") if tmp[1].find("<<<") == -1 else tmp[1].find("<<<")
            idx2 = float("inf") if tmp[1].find("\n\n") == -1 else tmp[1].find("\n\n")
            idx = min(idx1, idx2)
        else:
            idx = float("inf") if tmp[1].find("<<<") == -1 else tmp[1].find("<<<")
        if idx == float("inf"):
            idx = len(tmp[1])
        code = tmp[1][:idx]
        # check natural language in code
        if code.find(":\n") != -1:
            code = code.split(":\n")[1]
        code = code.strip()
        if code.startswith("```java"):
            code = code.split("```java")[1].split("```")[0]
        elif code.startswith("```"):
            code = code.split("```")[1]
        # Match brackets
        code = matchBracket(code)
        return code
    except Exception as e:
        return ""


def loadCodeFromFile(path, model):
    cseg_list = []
    f = open(path)
    result = json.load(f)
    f.close()
    if model == "gpt":
        # We only evaluate pass@k for gpt now
        for choice in result["choices"]:
            cseg = getCodeSegment(choice["message"]["content"], model)
            cseg_list.append(cseg)
    elif model == "llama" or model == "polycoder" or model == "vicuna":
        cseg = getCodeSegment(result["response"], model)
        cseg_list.append(cseg)
    elif model == "deepseek":
        # truncate prompt from the response
        cseg = getCodeSegment(result["response"][len(result["prompt"]) :], model)
        cseg_list.append(cseg)
    return cseg_list


def answerChecker(answer_dir, dataset_path, pattern_path, model, passk=1):
    ct = Counter()
    dataset = loadDataset(dataset_path)
    flist = glob.glob(answer_dir + "/*.json")
    for fname in tqdm.tqdm(flist):
        ct.total_count += 1
        # Read the answer
        code_segments = loadCodeFromFile(fname, model)
        ct.check(code_segments, pattern_path, passk)
    ct.print()


if __name__ == "__main__":
    fire.Fire(answerChecker)