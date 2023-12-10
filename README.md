# RobustAPI

The official repo for the paper [Can ChatGPT replace StackOverflow? A Study on Robustness and Reliability of Large Language Model Code Generation](https://arxiv.org/abs/2308.10335) (AAAI'24). 

![Alt text](image.png)

In this dataset, we collect 1208 coding questions (`dataset/question.jsonl`) from StackOverflow on 24 representative Java APIs (see the details in `dataset/api_list.txt`). We summarize the
use patterns of these APIs (`eval/pat_list.txt`) and evaluate them on popular LLMs including GPT-3.5, GPT-4, Llama, PolyCoder and Vicuna.

# Setup

See [llama](https://github.com/facebookresearch/llama), [Vicuna](https://github.com/vicuna-tools/vicuna-installation-guide), [GPT](https://platform.openai.com/docs/guides/gpt) for setting up instructions.

Install the dependencies:
```
pip install -r requirements.txt
```

# Prompts

To generate responses from the large language models, see scripts in `scripts/ask*`.

# Evaluator
To evaluate the API misuse rate in the question answers, see scripts in `scripts/eval*`

Since the API checker is written in Java, you need to have [Java Runtime Environment](https://ubuntu.com/tutorials/install-jre) installed on your machine. In our experimetns, it is validated to work under version `OpenJDK 11.0.20.1`.

We acknowledge ICSE'18 paper [ExampleCheck](https://tianyi-zhang.github.io/files/icse2018-examplecheck.pdf), based on which we build the checker.

# Results of Evaluation
The code responses are in `results/`. Each model has a directory, in which every json file corresponds to the response from the large language model to the Stack Overflow questions. The numbering follows the same numbering in `dataset/question.jsonl`.

If you find our work useful, please cite the paper:
```
@misc{zhong2023chatgpt,
      title={Can ChatGPT replace StackOverflow? A Study on Robustness and Reliability of Large Language Model Code Generation}, 
      author={Li Zhong and Zilong Wang},
      year={2023},
      eprint={2308.10335},
      archivePrefix={arXiv},
      primaryClass={cs.CL}
}
```


