import json

def generatePrompt(api, question, shots):
    prompt = "Please answer my code questions using the given API following this format: <<<api>>>: $API\n<<<code>>>: $CODE\n<<<explanation>>>: $EXPLANATION.\n"
    for i, shot in enumerate(shots):
        prompt += "Question: " + shot['question'] + "\nPlease using this api: %s." % shot['api'] + "\nAnswer:<<<api>>>: %s\n<<<code>>>: %s\n<<<explanation>>>: %s\n" % (shot['api'], shot['code'], shot['explanation'])
    prompt += "Question: " + question + "\nPlease using this api: %s." % api + "\nAnswer:"
    return prompt

def generateShot(api, number):
    if number == -1:
        fake_shot = {
            'api': 'Arrays.stream',
            'question': 'How can I calculate the sum of an array in Java?',
            'code': 'int[] array = {1, 2, 3, 4, 5};\nint sum = Arrays.stream(array).sum();',
            'explanation': 'The sum() method of the IntStream class returns the sum of elements in this stream. This is a special case of a reduction. This is a terminal operation.',
            'origin': 'https://stackoverflow.com/questions/xxx'
        }
        return [fake_shot]  
    if number == 0:
        return []
    elif number == 1:
        f_shot = open('dataset/shot_examples/' + api + '.json')
        shots = json.load(f_shot)
        f_shot.close()
        return [shots]
    else:
        raise Exception('generateShot() function incomplete!')
